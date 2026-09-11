package calebxzau.rdi.client.modcatalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.time.Clock
import java.util.ArrayDeque

internal class DefaultModCatalog(
    private val adapters: Map<ModPlatform, PlatformAdapter>,
    private val identityIndex: CatalogIdentityIndex,
    private val cachePolicy: CatalogCachePolicy,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
    private val onWarning: (Throwable) -> Unit
) : ModCatalog {
    private val projectCache = MemoryCatalogCache<CatalogProjectRef, CatalogProjectSource>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl
    )
    private val slugCache = MemoryCatalogCache<SlugTargetKey, CatalogProjectSource>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl
    )
    private val detailsCache = MemoryCatalogCache<CatalogProjectRef, CatalogModDetailsSource>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl
    )
    private val fileCache = MemoryCatalogCache<CatalogFileRef, CatalogFile>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl
    )
    private val fileListCache = MemoryCatalogCache<FileListKey, AdapterFileList>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl,
        maxSize = FILE_LIST_CACHE_SIZE
    )
    private val fileLoadMutexes = adapters.keys.associateWith { Mutex() }
    private val changelogCache = MemoryCatalogCache<CatalogFileRef, String>(
        clock,
        cachePolicy.metadataTtl,
        cachePolicy.negativeTtl
    )

    override suspend fun getMetadata(
        refs: Set<CatalogSlugRef>
    ): Result<Map<CatalogSlugRef, CatalogModMetadata>> = catalogResult {
        identityIndex.findAll(refs).mapValues { (ref, record) -> record.toMetadata(ref.platform) }
    }

    override suspend fun searchMetadata(
        query: String,
        limit: Int
    ): Result<List<CatalogModMetadata>> = catalogResult {
        require(limit > 0) { "Metadata search limit must be positive" }
        identityIndex.search(query, 0, limit).map { it.toMetadata() }
    }

    override suspend fun search(
        request: CatalogSearchRequest
    ): Result<CatalogOutcome<CatalogSearchPage>> = catalogResult {
        val requestKey = request.requestKey()
        val hasQuery = request.query.isNotBlank()
        val initial = request.cursor?.state ?: SearchCursorState(
            requestKey = requestKey,
            localOffset = 0,
            localExhausted = !hasQuery,
            localBuffer = emptyList(),
            fallbackQuery = null,
            platformOffsets = adapters.keys.associateWith { 0 },
            platformBuffers = emptyMap(),
            exhausted = emptySet(),
            unavailablePlatforms = emptySet(),
            seenIdentities = emptySet()
        )
        require(initial.requestKey == requestKey) { "Search cursor belongs to a different request" }

        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        val failures = linkedMapOf<ModPlatform, Throwable>()
        val unavailablePlatforms = initial.unavailablePlatforms.toMutableSet()
        var localOffset = initial.localOffset
        var localExhausted = initial.localExhausted
        var fallbackQuery = initial.fallbackQuery
        val seen = initial.seenIdentities.toMutableSet()
        val localBuffer = initial.localBuffer.toMutableList()
        val buffers = initial.platformBuffers.mapValuesTo(linkedMapOf()) { it.value.toMutableList() }
        val offsets = initial.platformOffsets.toMutableMap()
        val exhausted = initial.exhausted.toMutableSet()

        if (hasQuery && localBuffer.size < request.pageSize && !localExhausted) {
            val candidateLimit = minOf(maxOf(request.pageSize * 2, 20), 50)
            val records = try {
                identityIndex.search(request.query, localOffset, candidateLimit)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                onWarning(cause)
                issues += CatalogIssue.IdentityIndexUnavailable(cause.message ?: cause.toString())
                localExhausted = true
                emptyList()
            }
            localOffset += records.size
            localExhausted = localExhausted || records.size < candidateLimit
            fallbackQuery = fallbackQuery ?: if (request.query.containsHan()) {
                records.firstOrNull()?.name ?: request.query
            } else {
                request.query
            }
            val resolved = resolveIdentityCandidates(records, request.target)
            resolved.failures.forEach { (platform, cause) ->
                failures[platform] = cause
                unavailablePlatforms += platform
                issues += CatalogIssue.SourceFailed(platform, cause.message ?: cause.toString())
            }
            val bufferedKeys = localBuffer.mapTo(mutableSetOf()) { it.identity.stableKey }
            resolved.mods.forEach { mod ->
                if (mod.identity.stableKey !in seen && bufferedKeys.add(mod.identity.stableKey)) localBuffer += mod
            }
        }

        val page = mutableListOf<CatalogMod>()
        val retainedLocal = mutableListOf<CatalogMod>()
        localBuffer.forEach { mod ->
            when {
                mod.identity.stableKey in seen -> Unit
                page.size < request.pageSize -> {
                    seen += mod.identity.stableKey
                    page += mod
                }
                else -> retainedLocal += mod
            }
        }

        val fetches = if (page.size < request.pageSize) supervisorScope {
            adapters.values.mapNotNull { adapter ->
                val buffered = buffers[adapter.platform].orEmpty()
                if (buffered.isNotEmpty() || adapter.platform in exhausted ||
                    adapter.platform in unavailablePlatforms
                ) return@mapNotNull null
                async {
                    runSource(adapter.platform) {
                        adapter.search(
                            query = if (hasQuery) fallbackQuery ?: request.query else request.query,
                            target = request.target,
                            sort = request.sort,
                            offset = offsets[adapter.platform] ?: 0,
                            limit = request.pageSize
                        )
                    }
                }
            }.awaitAll()
        } else {
            emptyList()
        }
        val successfulFetches = fetches.filterIsInstance<SourceCall.Success<SourcePage>>()
        fetches.filterIsInstance<SourceCall.Failure>().forEach {
            failures[it.platform] = it.cause
            unavailablePlatforms += it.platform
            issues += CatalogIssue.SourceFailed(it.platform, it.cause.message ?: it.cause.toString())
        }
        successfulFetches.forEach { result ->
            buffers.getOrPut(result.platform, ::mutableListOf) += result.value.items
            result.value.nextOffset?.let { offsets[result.platform] = it } ?: exhausted.add(result.platform)
        }

        val remoteSources = buffers.values.flatten()
        val groupedRemote = groupSourcesByIdentity(remoteSources, issues)
        val remoteRanks = buffers.values.flatMap { sources ->
            sources.mapIndexed { index, source -> source.ref to index }
        }.toMap()
        val rankedRemoteMods = groupedRemote.groups.map { (record, sources) ->
            RankedCatalogMod(
                mod = sources.toCatalogMod(record),
                relevanceRank = sources.minOf { remoteRanks.getValue(it.ref) }
            )
        }
        val orderedRemoteMods = if (request.sort == CatalogSort.RELEVANCE) {
            rankedRemoteMods.sortedWith(
                compareBy<RankedCatalogMod>(RankedCatalogMod::relevanceRank)
                    .thenByDescending { it.mod.downloadCount }
                    .thenBy { it.mod.identity.stableKey }
            ).map(RankedCatalogMod::mod)
        } else {
            rankedRemoteMods.map(RankedCatalogMod::mod).sortedWith(request.sort.comparator())
        }
        val retainedLocalKeys = retainedLocal.mapTo(mutableSetOf()) { it.identity.stableKey }
        orderedRemoteMods.forEach { mod ->
            if (page.size < request.pageSize && mod.identity.stableKey !in retainedLocalKeys &&
                seen.add(mod.identity.stableKey)
            ) {
                page += mod
            }
        }

        val retainedIdentities = orderedRemoteMods.asSequence()
            .filter { it.identity.stableKey !in seen && it.identity.stableKey !in retainedLocalKeys }
            .map { it.identity.stableKey }
            .toSet()
        buffers.keys.forEach { platform ->
            buffers[platform] = buffers[platform].orEmpty().filterTo(mutableListOf()) { source ->
                groupedRemote.identityByRef[source.ref]?.stableKey in retainedIdentities
            }
        }

        if (initial.seenIdentities.isEmpty() &&
            page.isEmpty() && retainedLocal.isEmpty() && failures.isNotEmpty() &&
            adapters.keys.all { it in unavailablePlatforms }
        ) {
            throw CatalogException.AllSourcesFailed(failures)
        }

        val hasNext = retainedLocal.isNotEmpty() ||
            (hasQuery && !localExhausted) ||
            buffers.values.any { it.isNotEmpty() } ||
            adapters.keys.any { it !in exhausted && it !in unavailablePlatforms }
        val nextState = if (hasNext) {
            SearchCursorState(
                requestKey = requestKey,
                localOffset = localOffset,
                localExhausted = localExhausted,
                localBuffer = retainedLocal,
                fallbackQuery = fallbackQuery,
                platformOffsets = offsets,
                platformBuffers = buffers,
                exhausted = exhausted,
                unavailablePlatforms = unavailablePlatforms,
                seenIdentities = seen
            )
        } else {
            null
        }
        CatalogOutcome(
            CatalogSearchPage(
                items = page,
                nextCursor = nextState?.let(::CatalogSearchCursor),
                estimatedTotal = if (hasQuery) null else successfulFetches
                    .mapNotNull { it.value.totalCount }
                    .takeIf { it.isNotEmpty() }
                    ?.sum()
            ),
            issues
        )
    }

    override suspend fun getMods(
        refs: Set<CatalogProjectRef>
    ): Result<CatalogOutcome<Map<CatalogProjectRef, CatalogMod>>> = catalogResult {
        if (refs.isEmpty()) return@catalogResult CatalogOutcome(emptyMap())
        val loadedProjects = loadProjects(refs.toList())
        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        val sources = loadedProjects.found.toMutableMap()
        loadedProjects.missing.forEach { ref ->
            issues += CatalogIssue.MissingProjects(setOf(ref))
        }
        loadedProjects.failures.forEach { (platform, cause) ->
            issues += CatalogIssue.SourceFailed(platform, cause.message ?: cause.toString())
        }
        if (sources.isEmpty() && loadedProjects.successfulPlatforms.isEmpty() && loadedProjects.failures.isNotEmpty()) {
            throw CatalogException.AllSourcesFailed(loadedProjects.failures)
        }
        val groupedSources = groupSourcesByIdentity(sources.values.toList(), issues)
        val logicalByIdentity = groupedSources.groups
            .map { (record, grouped) -> grouped.toCatalogMod(record) }
            .associateBy { it.identity.stableKey }
        val found = sources.mapValues { (_, source) ->
            logicalByIdentity.getValue(groupedSources.identityByRef.getValue(source.ref).stableKey)
        }
        CatalogOutcome(found, issues)
    }

    override suspend fun getFiles(
        refs: Set<CatalogFileRef>
    ): Result<CatalogOutcome<Map<CatalogFileRef, CatalogFile>>> = catalogResult {
        if (refs.isEmpty()) return@catalogResult CatalogOutcome(emptyMap())
        val calls = supervisorScope {
            refs.groupBy(CatalogFileRef::platform).map { (platform, platformRefs) ->
                async {
                    platformRefs to runSource(platform) {
                        loadPlatformFiles(platform, platformRefs)
                    }
                }
            }.awaitAll()
        }
        val files = linkedMapOf<CatalogFileRef, CatalogFile>()
        val issues = mutableListOf<CatalogIssue>()
        calls.forEach { (platformRefs, result) ->
            when (result) {
                is SourceCall.Success -> {
                    platformRefs.forEach { ref ->
                        result.value.found[ref.fileId]?.let { file ->
                            files[ref] = file
                        }
                    }
                    val missing = platformRefs.filterTo(linkedSetOf()) { it.fileId in result.value.missing }
                    if (missing.isNotEmpty()) issues += CatalogIssue.MissingFiles(missing)
                }

                is SourceCall.Failure -> issues += CatalogIssue.SourceFailed(
                    result.platform,
                    result.cause.message ?: result.cause.toString()
                )
            }
        }
        if (files.isEmpty() && calls.all { it.second is SourceCall.Failure }) {
            throw CatalogException.AllSourcesFailed(
                calls.mapNotNull { (_, value) ->
                    (value as? SourceCall.Failure)?.let { it.platform to it.cause }
                }.toMap()
            )
        }
        CatalogOutcome(files, issues)
    }

    override suspend fun getDetails(
        request: CatalogDetailsRequest
    ): Result<CatalogOutcome<CatalogModDetails>> = catalogResult {
        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        var details: CatalogModDetailsSource? = null
        for (source in request.mod.sources.sortedBy { if (it.ref == request.mod.primaryRef) 0 else 1 }) {
            when (val result = runSource(source.ref.platform) {
                detailsCache.getOrLoad(source.ref) {
                    adapters.getValue(source.ref.platform).getDetails(source.ref)
                }
            }) {
                is SourceCall.Success -> {
                    details = result.value
                    if (details != null) break
                }
                is SourceCall.Failure -> issues += CatalogIssue.SourceFailed(
                    result.platform,
                    result.cause.message ?: result.cause.toString()
                )
            }
        }
        val loadedDetails = details ?: throw CatalogException.AllSourcesFailed(
            issues.filterIsInstance<CatalogIssue.SourceFailed>().associate {
                it.platform to IllegalStateException(it.message)
            }
        )
        val identity = identityFor(loadedDetails.project).second
        CatalogOutcome(
            CatalogModDetails(
                mod = request.mod,
                description = loadedDetails.description.ifBlank { identity?.intro.orEmpty() },
                authors = loadedDetails.authors,
                categories = loadedDetails.categories,
                sourceUrl = loadedDetails.sourceUrl,
                issuesUrl = loadedDetails.issuesUrl,
                wikiUrl = loadedDetails.wikiUrl
            ),
            issues
        )
    }

    override suspend fun listFiles(
        request: CatalogFileRequest
    ): Result<CatalogOutcome<CatalogFilePage>> = catalogResult {
        val requestedPlatform = request.cursor?.platform
        val sources = request.mod.sources
            .filter { requestedPlatform == null || it.ref.platform == requestedPlatform }
            .sortedBy { if (it.ref.platform == ModPlatform.MODRINTH) 0 else 1 }
        val issues = mutableListOf<CatalogIssue>()
        var selectedPlatform: ModPlatform? = null
        var selectedFiles = emptyList<CatalogFile>()
        var nextOffset: Int? = null
        var successfulSource = false
        for (source in sources) {
            val result = runSource(source.ref.platform) {
                loadFileList(source.ref, request.target, request.cursor?.offset ?: 0, request.pageSize)
            }
            when (result) {
                is SourceCall.Success -> {
                    successfulSource = true
                    selectedPlatform = source.ref.platform
                    when (val list = result.value) {
                        is AdapterFileList.Complete -> {
                            val compatible = list.items.filter { it.channel in request.channels }
                            val offset = request.cursor?.offset ?: 0
                            selectedFiles = compatible.drop(offset).take(request.pageSize)
                            nextOffset = (offset + selectedFiles.size)
                                .takeIf { selectedFiles.isNotEmpty() && it < compatible.size }
                        }
                        is AdapterFileList.Page -> {
                            selectedFiles = list.items.filter { it.channel in request.channels }
                            nextOffset = list.nextOffset
                        }
                    }
                    if (selectedFiles.isNotEmpty() || requestedPlatform != null) break
                }

                is SourceCall.Failure -> issues += CatalogIssue.SourceFailed(
                    result.platform,
                    result.cause.message ?: result.cause.toString()
                )
            }
        }
        if (!successfulSource) {
            throw CatalogException.AllSourcesFailed(
                issues.filterIsInstance<CatalogIssue.SourceFailed>().associate {
                    it.platform to IllegalStateException(it.message)
                }
            )
        }
        CatalogOutcome(
            CatalogFilePage(
                selectedFiles,
                nextOffset?.let { CatalogFileCursor(requireNotNull(selectedPlatform), it) }
            ),
            issues
        )
    }

    override suspend fun matchFiles(
        hashes: List<CatalogFileHashes>
    ): Result<Map<String, CatalogFile>> = catalogResult {
        if (hashes.isEmpty()) return@catalogResult emptyMap()
        val calls = supervisorScope {
            adapters.values.map { adapter ->
                async { runSource(adapter.platform) { adapter.matchFiles(hashes) } }
            }.awaitAll()
        }
        val failures = calls.filterIsInstance<SourceCall.Failure>()
        if (calls.none { it is SourceCall.Success<*> }) {
            throw CatalogException.AllSourcesFailed(failures.associate { it.platform to it.cause })
        }

        val matches = linkedMapOf<String, CatalogFile>()
        listOf(ModPlatform.CURSEFORGE, ModPlatform.MODRINTH).forEach { platform ->
            calls.filterIsInstance<SourceCall.Success<Map<String, CatalogFile>>>()
                .firstOrNull { it.platform == platform }
                ?.value
                ?.forEach { (key, file) ->
                    matches[key] = file
                    fileCache.put(file.ref, file)
                }
        }
        matches
    }

    override suspend fun matchLocalFiles(
        paths: List<Path>
    ): Result<CatalogOutcome<LocalFileMatchReport>> = catalogResult {
        val hashes = mutableListOf<LocalFileHashes>()
        val unreadable = linkedMapOf<Path, String>()
        paths.distinct().forEach { path ->
            try {
                hashes += hashLocalFile(path, ioDispatcher)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                onWarning(cause)
                unreadable[path] = cause.message ?: cause::class.simpleName.orEmpty()
            }
        }
        if (hashes.isEmpty()) {
            return@catalogResult CatalogOutcome(
                LocalFileMatchReport(emptyList(), emptySet(), unreadable),
                listOfNotNull(identityIssue())
            )
        }
        val calls = supervisorScope {
            adapters.values.map { adapter ->
                async { runSource(adapter.platform) { adapter.matchLocalFiles(hashes) } }
            }.awaitAll()
        }
        val failures = calls.filterIsInstance<SourceCall.Failure>()
        val successes = calls.filterIsInstance<SourceCall.Success<Map<Path, CatalogFile>>>()
        if (successes.isEmpty()) {
            throw CatalogException.AllSourcesFailed(failures.associate { it.platform to it.cause })
        }
        val byPath = linkedMapOf<Path, MutableList<CatalogFile>>()
        successes.forEach { result ->
            result.value.forEach { (path, file) ->
                fileCache.put(file.ref, file)
                byPath.getOrPut(path, ::mutableListOf) += file
            }
        }
        val loadedProjects = loadProjects(
            byPath.values.asSequence()
                .flatten()
                .map(CatalogFile::project)
                .distinct()
                .toList()
        )
        val identities = loadedProjects.found.mapValues { (_, project) -> identityFor(project).first }
        val matched = byPath.map { (path, files) ->
            LocalFileMatch(path, files, files.firstNotNullOfOrNull { identities[it.project] })
        }
        CatalogOutcome(
            LocalFileMatchReport(
                matched = matched,
                unmatched = hashes.mapTo(linkedSetOf(), LocalFileHashes::path) - byPath.keys,
                unreadable = unreadable
            ),
            buildList {
                identityIssue()?.let(::add)
                failures.forEach { add(CatalogIssue.SourceFailed(it.platform, it.cause.message ?: it.cause.toString())) }
                loadedProjects.failures.forEach { (platform, cause) ->
                    add(CatalogIssue.SourceFailed(platform, cause.message ?: cause.toString()))
                }
            }
        )
    }

    override suspend fun findUpdates(
        request: UpdateRequest
    ): Result<CatalogOutcome<UpdateReport>> = catalogResult {
        val updates = mutableListOf<UpdateCandidate>()
        val unchanged = linkedSetOf<CatalogFileRef>()
        val unresolved = linkedSetOf<CatalogFileRef>()
        val issues = mutableListOf<CatalogIssue>()
        var successful = 0
        for (installed in request.installed) {
            when (val result = runSource(installed.file.ref.platform) {
                loadFileList(
                    installed.file.project,
                    installed.target,
                    0,
                    50
                ).items.firstOrNull { it.channel in installed.file.channel.compatibleChannels() }
            }) {
                is SourceCall.Success -> {
                    successful++
                    val latest = result.value
                    when {
                        latest == null -> unresolved += installed.file.ref
                        latest.ref == installed.file.ref -> unchanged += installed.file.ref
                        latest.publishedAt.isAfter(installed.file.publishedAt) ->
                            updates += UpdateCandidate(installed.file, latest)
                        else -> unchanged += installed.file.ref
                    }
                }

                is SourceCall.Failure -> {
                    unresolved += installed.file.ref
                    issues += CatalogIssue.SourceFailed(
                        result.platform,
                        result.cause.message ?: result.cause.toString()
                    )
                }
            }
        }
        if (request.installed.isNotEmpty() && successful == 0) {
            throw CatalogException.AllSourcesFailed(
                issues.filterIsInstance<CatalogIssue.SourceFailed>().associate {
                    it.platform to IllegalStateException(it.message)
                }
            )
        }
        CatalogOutcome(UpdateReport(updates, unchanged, unresolved), issues)
    }

    override suspend fun resolveDependencies(
        request: DependencyRequest
    ): Result<CatalogOutcome<DependencyGraph>> = catalogResult {
        val roots = request.roots.mapTo(linkedSetOf(), CatalogFile::ref)
        val nodes = linkedMapOf<CatalogFileRef, DependencyNode>()
        val edges = mutableListOf<DependencyEdge>()
        val unresolved = mutableListOf<UnresolvedDependency>()
        val cycles = mutableListOf<List<CatalogFileRef>>()
        val issues = mutableListOf<CatalogIssue>()
        val queue = ArrayDeque<Traversal>()
        request.roots.forEach {
            nodes[it.ref] = DependencyNode(it, 0)
            queue += Traversal(it, listOf(it.ref), 0)
        }
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val current = queue.removeFirst()
            current.file.dependencies.forEach { dependency ->
                if (dependency.requirement !in request.requirements) return@forEach
                edges += DependencyEdge(current.file.ref, dependency.target, dependency.requirement)
                val platform = dependency.target.platform
                val resolved = when (val result = runSource(platform) {
                    resolveDependency(
                        dependency.target,
                        request.target,
                        current.file.channel.compatibleChannels()
                    )
                }) {
                    is SourceCall.Success -> result.value
                    is SourceCall.Failure -> {
                        issues += CatalogIssue.SourceFailed(
                            result.platform,
                            result.cause.message ?: result.cause.toString()
                        )
                        null
                    }
                }
                if (resolved == null) {
                    unresolved += UnresolvedDependency(
                        current.file.ref,
                        dependency.target,
                        "找不到兼容版本"
                    )
                    return@forEach
                }
                if (resolved.ref in current.path) {
                    cycles += current.path.dropWhile { it != resolved.ref } + resolved.ref
                    return@forEach
                }
                val nextDepth = current.depth + 1
                val known = nodes[resolved.ref]
                if (known == null || nextDepth < known.depth) {
                    nodes[resolved.ref] = DependencyNode(resolved, nextDepth)
                    queue += Traversal(resolved, current.path + resolved.ref, nextDepth)
                }
            }
        }
        CatalogOutcome(DependencyGraph(roots, nodes, edges, unresolved, cycles), issues)
    }

    override suspend fun getChangelog(file: CatalogFileRef): Result<String> = catalogResult {
        changelogCache.getOrLoad(file) { adapters.getValue(file.platform).getChangelog(file) }.orEmpty()
    }

    override suspend fun resolveDownload(file: CatalogFile): Result<ResolvedDownload> = catalogResult {
        adapters.getValue(file.ref.platform).resolveDownload(file)
    }

    override fun close() {
        identityIndex.close()
    }

    private suspend fun groupSourcesByIdentity(
        sources: List<CatalogProjectSource>,
        issues: MutableList<CatalogIssue>
    ): GroupedSources {
        if (sources.isEmpty()) return GroupedSources(emptyList(), emptyMap())
        val slugRefs = sources.mapTo(linkedSetOf()) { CatalogSlugRef(it.ref.platform, it.slug) }
        val records = try {
            identityIndex.findAll(slugRefs)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            onWarning(cause)
            issues += CatalogIssue.IdentityIndexUnavailable(cause.message ?: cause.toString())
            emptyMap()
        }
        val recordsBySource = sources.associateWith { source ->
            records[CatalogSlugRef(source.ref.platform, source.slug)]
                ?.takeIf { source.contentType == CatalogContentType.MOD }
        }
        val identityByRef = sources.associate { source ->
            val record = recordsBySource[source]
            source.ref to (record?.let { CatalogIdentity.Mcmod(it.mcmodId) }
                ?: CatalogIdentity.Unmapped(source.ref))
        }
        val groups = sources.groupBy { identityByRef.getValue(it.ref).stableKey }.values.map { group ->
            recordsBySource[group.first()] to group
        }
        return GroupedSources(groups, identityByRef)
    }

    private suspend fun resolveIdentityCandidates(
        records: List<CatalogIdentityRecord>,
        target: CatalogTarget
    ): LocalResolution = supervisorScope {
        if (records.isEmpty()) return@supervisorScope LocalResolution(emptyList(), emptyMap())
        val resolutions = listOfNotNull(
            adapters[ModPlatform.MODRINTH]?.let { adapter ->
                async { resolveModrinthCandidates(adapter, records, target) }
            },
            adapters[ModPlatform.CURSEFORGE]?.let { adapter ->
                async { resolveCurseForgeCandidates(adapter, records, target) }
            }
        ).awaitAll()
        val failures = resolutions.mapNotNull { resolution ->
            resolution.failure?.let { resolution.platform to it }
        }.toMap()

        LocalResolution(
            mods = records.mapNotNull { record ->
                resolutions.mapNotNull { it.sources[record.mcmodId] }
                    .takeIf { it.isNotEmpty() }
                    ?.toCatalogMod(record)
            },
            failures = failures
        )
    }

    private suspend fun resolveModrinthCandidates(
        adapter: PlatformAdapter,
        records: List<CatalogIdentityRecord>,
        target: CatalogTarget
    ): CandidateResolution {
        val slugs = records.flatMap { record ->
            record.projects.filter { it.platform == ModPlatform.MODRINTH }.map(CatalogIdentityProject::slug)
        }
        return when (val result = runSource(adapter.platform) { resolveCachedSlugs(adapter, slugs, target) }) {
            is SourceCall.Success -> CandidateResolution(
                platform = adapter.platform,
                sources = records.mapNotNull { record ->
                    record.projects.asSequence()
                        .filter { it.platform == ModPlatform.MODRINTH }
                        .mapNotNull { result.value[normalizeProjectSlug(it.slug)] }
                        .firstOrNull()
                        ?.let { record.mcmodId to it }
                }.toMap()
            )
            is SourceCall.Failure -> CandidateResolution(adapter.platform, failure = result.cause)
        }
    }

    private suspend fun resolveCurseForgeCandidates(
        adapter: PlatformAdapter,
        records: List<CatalogIdentityRecord>,
        target: CatalogTarget
    ): CandidateResolution {
        val sources = linkedMapOf<Int, CatalogProjectSource>()
        var requestBudget = CURSEFORGE_SLUG_REQUEST_BUDGET
        for (record in records) {
            for (project in record.projects.filter { it.platform == ModPlatform.CURSEFORGE }) {
                val key = project.slug.toSlugTargetKey(adapter.platform, target)
                val cached = slugCache.get(key)
                if (cached == null && requestBudget == 0) break
                if (cached == null) requestBudget--
                when (val result = runSource(adapter.platform) {
                    resolveCachedSlug(adapter, project.slug, target, cached)
                }) {
                    is SourceCall.Success -> if (result.value != null) {
                        sources[record.mcmodId] = result.value
                        break
                    }
                    is SourceCall.Failure -> return CandidateResolution(
                        platform = adapter.platform,
                        sources = sources,
                        failure = result.cause
                    )
                }
            }
        }
        return CandidateResolution(adapter.platform, sources)
    }

    private suspend fun resolveCachedSlugs(
        adapter: PlatformAdapter,
        slugs: List<String>,
        target: CatalogTarget
    ): Map<String, CatalogProjectSource> {
        val found = linkedMapOf<String, CatalogProjectSource>()
        val uncached = mutableListOf<String>()
        slugs.distinctBy(::normalizeProjectSlug).forEach { slug ->
            val normalized = normalizeProjectSlug(slug)
            val cached = slugCache.get(slug.toSlugTargetKey(adapter.platform, target))
            if (cached == null) uncached += slug else cached.value?.let { found[normalized] = it }
        }
        if (uncached.isEmpty()) return found
        val resolved = adapter.resolveSlugs(uncached, target)
        uncached.forEach { slug ->
            val normalized = normalizeProjectSlug(slug)
            val source = resolved.found[normalized]
            slugCache.put(slug.toSlugTargetKey(adapter.platform, target), source)
            source?.let {
                projectCache.put(it.ref, it)
                found[normalized] = it
            }
        }
        return found
    }

    private suspend fun resolveCachedSlug(
        adapter: PlatformAdapter,
        slug: String,
        target: CatalogTarget,
        cached: CachedCatalogValue<CatalogProjectSource>?
    ): CatalogProjectSource? {
        cached?.let { return it.value }
        val normalized = normalizeProjectSlug(slug)
        val source = adapter.resolveSlugs(listOf(slug), target).found[normalized]
        slugCache.put(slug.toSlugTargetKey(adapter.platform, target), source)
        source?.let { projectCache.put(it.ref, it) }
        return source
    }

    private suspend fun identityFor(
        source: CatalogProjectSource
    ): Pair<CatalogIdentity, CatalogIdentityRecord?> {
        if (source.contentType != CatalogContentType.MOD) {
            return CatalogIdentity.Unmapped(source.ref) to null
        }
        val record = try {
            identityIndex.find(source.ref.platform, source.slug)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            onWarning(cause)
            null
        }
        return (record?.let { CatalogIdentity.Mcmod(it.mcmodId) }
            ?: CatalogIdentity.Unmapped(source.ref)) to record
    }

    private fun List<CatalogProjectSource>.toCatalogMod(
        identityRecord: CatalogIdentityRecord?
    ): CatalogMod {
        val hasModSource = any { it.contentType == CatalogContentType.MOD }
        val primary = firstOrNull {
            it.contentType == CatalogContentType.MOD && it.ref.platform == ModPlatform.MODRINTH
        } ?: firstOrNull { it.contentType == CatalogContentType.MOD } ?: first()
        val identity = identityRecord
            ?.takeIf { hasModSource }
            ?.let { CatalogIdentity.Mcmod(it.mcmodId) }
            ?: CatalogIdentity.Unmapped(primary.ref)
        val identityData = identityRecord?.takeIf { identity is CatalogIdentity.Mcmod }
        return CatalogMod(
            identity = identity,
            primaryRef = primary.ref,
            sources = distinctBy(CatalogProjectSource::ref),
            name = identityData?.name ?: primary.name,
            nameCn = identityData?.projects
                ?.firstOrNull { it.platform == primary.ref.platform }
                ?.nameCnOverride
                ?: identityData?.nameCn,
            summary = identityData?.intro?.takeIf(String::isNotBlank) ?: primary.summary,
            mcmodIconUrl = identityData?.logoUrl,
            downloadCount = sumOf(CatalogProjectSource::downloadCount),
            updatedAt = maxOf(CatalogProjectSource::updatedAt),
            environment = primary.environment
        )
    }

    private suspend fun loadProjects(refs: List<CatalogProjectRef>): ProjectLoad {
        if (refs.isEmpty()) return ProjectLoad()

        val found = linkedMapOf<CatalogProjectRef, CatalogProjectSource>()
        val missing = linkedSetOf<CatalogProjectRef>()
        val failures = linkedMapOf<ModPlatform, Throwable>()
        val successfulPlatforms = linkedSetOf<ModPlatform>()
        val uncachedByPlatform = linkedMapOf<ModPlatform, MutableList<CatalogProjectRef>>()

        refs.distinct().forEach { ref ->
            when (val cached = projectCache.get(ref)) {
                null -> uncachedByPlatform.getOrPut(ref.platform, ::mutableListOf) += ref
                else -> {
                    successfulPlatforms += ref.platform
                    cached.value?.let { found[ref] = it } ?: run { missing += ref }
                }
            }
        }

        val calls = supervisorScope {
            uncachedByPlatform.map { (platform, platformRefs) ->
                async {
                    platformRefs to runSource(platform) {
                        adapters.getValue(platform).getProjects(
                            platformRefs.mapTo(linkedSetOf(), CatalogProjectRef::projectId)
                        )
                    }
                }
            }.awaitAll()
        }
        calls.forEach { (platformRefs, result) ->
            when (result) {
                is SourceCall.Success -> {
                    successfulPlatforms += platformRefs.first().platform
                    platformRefs.forEach { ref ->
                        val project = result.value.found[ref.projectId]
                        projectCache.put(ref, project)
                        if (project == null) {
                            missing += ref
                        } else {
                            found[ref] = project
                        }
                    }
                }

                is SourceCall.Failure -> failures[platformRefs.first().platform] = result.cause
            }
        }
        return ProjectLoad(found, missing, failures, successfulPlatforms)
    }

    private suspend fun resolveDependency(
        target: DependencyTarget,
        catalogTarget: CatalogTarget,
        channels: Set<ReleaseChannel>
    ): CatalogFile? = when (target) {
        is DependencyTarget.File -> loadPlatformFiles(target.ref.platform, listOf(target.ref))
            .found[target.ref.fileId]

        is DependencyTarget.Project -> findCompatibleProjectFile(target.ref, catalogTarget, channels)
    }

    private suspend fun findCompatibleProjectFile(
        project: CatalogProjectRef,
        target: CatalogTarget,
        channels: Set<ReleaseChannel>
    ): CatalogFile? {
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            when (val page = loadFileList(project, target, offset, PROJECT_DEPENDENCY_PAGE_SIZE)) {
                is AdapterFileList.Complete -> return page.items.firstOrNull { it.channel in channels }
                is AdapterFileList.Page -> {
                    page.items.firstOrNull { it.channel in channels }?.let { return it }
                    val nextOffset = page.nextOffset ?: return null
                    if (nextOffset <= offset) {
                        throw CatalogException.InvalidResponse(
                            project.platform,
                            IllegalStateException("File list offset did not advance: $offset -> $nextOffset")
                        )
                    }
                    offset = nextOffset
                }
            }
        }
    }

    private suspend fun loadPlatformFiles(
        platform: ModPlatform,
        refs: List<CatalogFileRef>
    ): AdapterResult<CatalogFile> {
        val mutex = fileLoadMutexes.getValue(platform)
        mutex.lock()
        try {
            val found = linkedMapOf<String, CatalogFile>()
            val missing = linkedSetOf<String>()
            val uncached = linkedSetOf<CatalogFileRef>()
            refs.distinct().forEach { ref ->
                when (val cached = fileCache.get(ref)) {
                    null -> uncached += ref
                    else -> cached.value?.let { found[ref.fileId] = it } ?: missing.add(ref.fileId)
                }
            }
            if (uncached.isNotEmpty()) {
                val loaded = adapters.getValue(platform).getFiles(
                    uncached.mapTo(linkedSetOf(), CatalogFileRef::fileId)
                )
                uncached.forEach { ref ->
                    val file = loaded.found[ref.fileId]
                    when {
                        file != null -> {
                            fileCache.put(ref, file)
                            found[ref.fileId] = file
                        }
                        ref.fileId in loaded.missing -> {
                            fileCache.put(ref, null)
                            missing += ref.fileId
                        }
                    }
                }
            }
            return AdapterResult(found, missing)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun loadFileList(
        project: CatalogProjectRef,
        target: CatalogTarget,
        offset: Int,
        limit: Int
    ): AdapterFileList {
        val key = FileListKey(
            project = project,
            target = target,
            offset = offset.takeIf { project.platform == ModPlatform.CURSEFORGE },
            limit = limit.takeIf { project.platform == ModPlatform.CURSEFORGE }
        )
        val list = fileListCache.getOrLoad(key) {
            adapters.getValue(project.platform).listFiles(project, target, offset, limit)
        } ?: error("File list cache loader returned null")
        list.items.forEach { fileCache.put(it.ref, it) }
        return list
    }

    private fun ReleaseChannel.compatibleChannels(): Set<ReleaseChannel> =
        if (this == ReleaseChannel.ALPHA) ReleaseChannel.entries.toSet()
        else setOf(ReleaseChannel.RELEASE, ReleaseChannel.BETA)

    private fun identityIssue(): CatalogIssue.IdentityIndexUnavailable? =
        identityIndex.unavailableCause?.let {
            CatalogIssue.IdentityIndexUnavailable(it.message ?: it.toString())
        }

    private suspend fun <T> runSource(platform: ModPlatform, block: suspend () -> T): SourceCall<T> = try {
        SourceCall.Success(platform, block())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        onWarning(cause)
        SourceCall.Failure(platform, cause)
    }

    private suspend fun <T> catalogResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Result.failure(cause)
    }

    private fun CatalogSearchRequest.requestKey(): String = buildString {
        append(query.trim())
        append('|').append(target.minecraftVersion.name)
        append('|').append(target.loader.name)
        append('|').append(sort.name)
        append('|').append(pageSize)
    }

    private fun String.toSlugTargetKey(platform: ModPlatform, target: CatalogTarget) = SlugTargetKey(
        platform = platform,
        slug = normalizeProjectSlug(this),
        minecraftVersion = target.minecraftVersion.mcVer,
        loader = target.loader.name
    )

    private fun String.containsHan(): Boolean = any {
        Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
    }

    private fun CatalogSort.comparator(): Comparator<CatalogMod> = when (this) {
        CatalogSort.RELEVANCE -> compareBy<CatalogMod> {
            if (it.primaryRef.platform == ModPlatform.MODRINTH) 0 else 1
        }.thenByDescending(CatalogMod::downloadCount)
            .thenBy { it.identity.stableKey }

        CatalogSort.DOWNLOADS -> compareByDescending<CatalogMod>(CatalogMod::downloadCount)
            .thenBy { it.identity.stableKey }
        CatalogSort.UPDATED -> compareByDescending<CatalogMod>(CatalogMod::updatedAt)
            .thenBy { it.identity.stableKey }
    }

    private data class Traversal(
        val file: CatalogFile,
        val path: List<CatalogFileRef>,
        val depth: Int
    )

    private data class GroupedSources(
        val groups: List<Pair<CatalogIdentityRecord?, List<CatalogProjectSource>>>,
        val identityByRef: Map<CatalogProjectRef, CatalogIdentity>
    )

    private data class LocalResolution(
        val mods: List<CatalogMod>,
        val failures: Map<ModPlatform, Throwable>
    )

    private data class CandidateResolution(
        val platform: ModPlatform,
        val sources: Map<Int, CatalogProjectSource> = emptyMap(),
        val failure: Throwable? = null
    )

    private data class ProjectLoad(
        val found: Map<CatalogProjectRef, CatalogProjectSource> = emptyMap(),
        val missing: Set<CatalogProjectRef> = emptySet(),
        val failures: Map<ModPlatform, Throwable> = emptyMap(),
        val successfulPlatforms: Set<ModPlatform> = emptySet()
    )

    private data class RankedCatalogMod(
        val mod: CatalogMod,
        val relevanceRank: Int
    )

    private data class SlugTargetKey(
        val platform: ModPlatform,
        val slug: String,
        val minecraftVersion: String,
        val loader: String
    )

    private data class FileListKey(
        val project: CatalogProjectRef,
        val target: CatalogTarget,
        val offset: Int?,
        val limit: Int?
    )

    private sealed interface SourceCall<out T> {
        val platform: ModPlatform

        data class Success<T>(override val platform: ModPlatform, val value: T) : SourceCall<T>

        data class Failure(override val platform: ModPlatform, val cause: Throwable) : SourceCall<Nothing>
    }

    companion object {
        private const val CURSEFORGE_SLUG_REQUEST_BUDGET = 3
        private const val FILE_LIST_CACHE_SIZE = 64
        private const val PROJECT_DEPENDENCY_PAGE_SIZE = 50

        fun create(
            httpClient: HttpClient,
            identityDatabaseMaterializationDir: Path,
            curseForgeConfig: CurseForgeConfig,
            modrinthConfig: ModrinthConfig,
            networkPolicy: CatalogNetworkPolicy,
            cachePolicy: CatalogCachePolicy,
            clock: Clock,
            ioDispatcher: CoroutineDispatcher,
            onWarning: (Throwable) -> Unit
        ): DefaultModCatalog {
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
            val index = SqliteCatalogIdentityIndex.openBundled(
                identityDatabaseMaterializationDir,
                ioDispatcher
            ).getOrElse { cause ->
                onWarning(cause)
                UnavailableIdentityIndex(cause)
            }
            val adapters = listOf(
                ModrinthAdapter(httpClient, modrinthConfig, networkPolicy, json, onWarning),
                CurseForgeAdapter(httpClient, curseForgeConfig, networkPolicy, json, onWarning)
            ).associateBy(PlatformAdapter::platform)
            return DefaultModCatalog(
                adapters,
                index,
                cachePolicy,
                clock,
                ioDispatcher,
                onWarning
            )
        }
    }
}

private fun CatalogIdentityRecord.toMetadata(platform: ModPlatform? = null) = CatalogModMetadata(
    mcmodId = mcmodId,
    name = name,
    nameCn = projects.firstOrNull { it.platform == platform }?.nameCnOverride ?: nameCn,
    intro = intro,
    logoUrl = logoUrl,
    projects = projects.map { CatalogMetadataProject(it.platform, it.slug, it.nameCnOverride) }
)
