package calebxzau.rdi.client.modcatalog

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
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
        if (request.cursor == null) {
            searchExactFullPinyin(request)?.let { return@catalogResult it }
        }
        val requestKey = request.requestKey()
        val initial = request.cursor?.state ?: SearchCursorState(
            requestKey = requestKey,
            platformOffsets = adapters.keys.associateWith { 0 },
            platformBuffers = emptyMap(),
            exhausted = emptySet(),
            seenIdentities = emptySet()
        )
        require(initial.requestKey == requestKey) { "Search cursor belongs to a different request" }

        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        val fetches = supervisorScope {
            adapters.values.mapNotNull { adapter ->
                val buffered = initial.platformBuffers[adapter.platform].orEmpty()
                if (buffered.isNotEmpty() || adapter.platform in initial.exhausted) return@mapNotNull null
                async {
                    runSource(adapter.platform) {
                        adapter.search(
                            query = request.query,
                            target = request.target,
                            sort = request.sort,
                            offset = initial.platformOffsets[adapter.platform] ?: 0,
                            limit = request.pageSize
                        )
                    }
                }
            }.awaitAll()
        }
        val successfulFetches = fetches.filterIsInstance<SourceCall.Success<SourcePage>>()
        fetches.filterIsInstance<SourceCall.Failure>().forEach {
            issues += CatalogIssue.SourceFailed(it.platform, it.cause.message ?: it.cause.toString())
        }
        val alreadyBuffered = initial.platformBuffers.values.any { it.isNotEmpty() }
        if (successfulFetches.isEmpty() && fetches.isNotEmpty() && !alreadyBuffered) {
            throw CatalogException.AllSourcesFailed(
                fetches.filterIsInstance<SourceCall.Failure>().associate { it.platform to it.cause }
            )
        }

        val buffers = initial.platformBuffers.mapValuesTo(linkedMapOf()) { it.value.toMutableList() }
        val offsets = initial.platformOffsets.toMutableMap()
        val exhausted = initial.exhausted.toMutableSet()
        successfulFetches.forEach { result ->
            buffers.getOrPut(result.platform, ::mutableListOf) += result.value.items
            result.value.nextOffset?.let { offsets[result.platform] = it } ?: exhausted.add(result.platform)
        }

        val remoteSources = buffers.values.flatten()
        val remoteGroups = groupSourcesByIdentity(remoteSources)
        val orderedRemoteMods = remoteGroups.map { (record, sources) -> sources.toCatalogMod(record) }
            .sortedWith(request.sort.comparator())
        val seen = initial.seenIdentities.toMutableSet()
        val page = buildList {
            orderedRemoteMods.forEach { mod ->
                if (size >= request.pageSize) return@forEach
                if (seen.add(mod.identity.stableKey)) add(mod)
            }
        }

        val retainedIdentities = orderedRemoteMods.asSequence()
            .filter { it.identity.stableKey !in seen }
            .map { it.identity.stableKey }
            .toSet()
        buffers.keys.forEach { platform ->
            buffers[platform] = buffers[platform].orEmpty().filterTo(mutableListOf()) { source ->
                identityFor(source).first.stableKey in retainedIdentities
            }
        }
        val hasNext = buffers.values.any { it.isNotEmpty() } ||
            exhausted.size < adapters.size
        val nextState = if (hasNext) {
            SearchCursorState(
                requestKey = requestKey,
                platformOffsets = offsets,
                platformBuffers = buffers,
                exhausted = exhausted,
                seenIdentities = seen
            )
        } else {
            null
        }
        CatalogOutcome(
            CatalogSearchPage(
                items = page,
                nextCursor = nextState?.let(::CatalogSearchCursor),
                estimatedTotal = successfulFetches.mapNotNull { it.value.totalCount }.takeIf { it.isNotEmpty() }?.sum()
            ),
            issues
        )
    }

    override suspend fun getMods(
        refs: Set<CatalogProjectRef>
    ): Result<CatalogOutcome<Map<CatalogProjectRef, CatalogMod>>> = catalogResult {
        if (refs.isEmpty()) return@catalogResult CatalogOutcome(emptyMap())
        val calls = supervisorScope {
            refs.map { ref -> async { ref to runSource(ref.platform) { loadProject(ref) } } }.awaitAll()
        }
        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        val sources = linkedMapOf<CatalogProjectRef, CatalogProjectSource>()
        calls.forEach { (ref, result) ->
            when (result) {
                is SourceCall.Success -> result.value?.let { sources[ref] = it }
                    ?: run { issues += CatalogIssue.MissingProjects(setOf(ref)) }

                is SourceCall.Failure -> issues += CatalogIssue.SourceFailed(
                    result.platform,
                    result.cause.message ?: result.cause.toString()
                )
            }
        }
        if (sources.isEmpty() && calls.all { it.second is SourceCall.Failure }) {
            throw CatalogException.AllSourcesFailed(
                calls.mapNotNull { (_, value) ->
                    (value as? SourceCall.Failure)?.let { it.platform to it.cause }
                }.toMap()
            )
        }
        val logicalByIdentity = groupSourcesByIdentity(sources.values.toList())
            .map { (record, grouped) -> grouped.toCatalogMod(record) }
            .associateBy { it.identity.stableKey }
        val found = sources.mapValues { (_, source) ->
            logicalByIdentity.getValue(identityFor(source).first.stableKey)
        }
        CatalogOutcome(found, issues)
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
                adapters.getValue(source.ref.platform).listFiles(
                    source.ref,
                    request.target,
                    request.channels,
                    request.cursor?.offset ?: 0,
                    request.pageSize
                )
            }
            when (result) {
                is SourceCall.Success -> {
                    successfulSource = true
                    selectedPlatform = source.ref.platform
                    selectedFiles = result.value.first
                    nextOffset = result.value.second
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
            result.value.forEach { (path, file) -> byPath.getOrPut(path, ::mutableListOf) += file }
        }
        val matched = byPath.map { (path, files) ->
            LocalFileMatch(path, files, files.firstNotNullOfOrNull { identityForFile(it) })
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
                adapters.getValue(installed.file.ref.platform).listFiles(
                    installed.file.project,
                    installed.target,
                    installed.file.channel.compatibleChannels(),
                    0,
                    50
                ).first.firstOrNull()
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

    private suspend fun searchExactFullPinyin(
        request: CatalogSearchRequest
    ): CatalogOutcome<CatalogSearchPage>? {
        val record = try {
            identityIndex.findExactFullPinyin(request.query)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            onWarning(cause)
            null
        } ?: return null
        val issues = mutableListOf<CatalogIssue>()
        identityIssue()?.let(issues::add)
        val mod = findProjectsForIdentity(record, request.target, issues)
            .takeIf { it.isNotEmpty() }
            ?.toCatalogMod(record)
        return CatalogOutcome(
            CatalogSearchPage(
                items = listOfNotNull(mod),
                nextCursor = null,
                estimatedTotal = if (mod == null) 0 else 1
            ),
            issues
        )
    }

    private suspend fun findProjectsForIdentity(
        record: CatalogIdentityRecord,
        target: CatalogTarget,
        issues: MutableList<CatalogIssue>
    ): List<CatalogProjectSource> = supervisorScope {
        val results = record.projects.distinctBy(CatalogIdentityProject::platform).map { project ->
            async {
                val adapter = adapters[project.platform] ?: return@async null
                when (val result = runSource(project.platform) {
                    adapter.findProjectBySlug(project.slug, target)
                }) {
                    is SourceCall.Success -> LocalProjectCall.Found(result.value)
                    is SourceCall.Failure -> LocalProjectCall.Failed(result.platform, result.cause)
                }
            }
        }.awaitAll().filterNotNull()
        results.filterIsInstance<LocalProjectCall.Failed>().forEach {
            issues += CatalogIssue.SourceFailed(it.platform, it.cause.message ?: it.cause.toString())
        }
        results.filterIsInstance<LocalProjectCall.Found>().mapNotNull(LocalProjectCall.Found::source)
    }

    private suspend fun groupSourcesByIdentity(
        sources: List<CatalogProjectSource>
    ): List<Pair<CatalogIdentityRecord?, List<CatalogProjectSource>>> {
        val records = sources.map { it to identityFor(it) }
        return records.groupBy { it.second.first.stableKey }.values.map { group ->
            group.first().second.second to group.map { it.first }
        }
    }

    private suspend fun identityFor(
        source: CatalogProjectSource
    ): Pair<CatalogIdentity, CatalogIdentityRecord?> {
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

    private suspend fun List<CatalogProjectSource>.toCatalogMod(
        identityRecord: CatalogIdentityRecord?
    ): CatalogMod {
        val primary = firstOrNull { it.ref.platform == ModPlatform.MODRINTH } ?: first()
        val identity = identityRecord?.let { CatalogIdentity.Mcmod(it.mcmodId) }
            ?: CatalogIdentity.Unmapped(primary.ref)
        return CatalogMod(
            identity = identity,
            primaryRef = primary.ref,
            sources = distinctBy(CatalogProjectSource::ref),
            name = identityRecord?.name ?: primary.name,
            nameCn = identityRecord?.projects
                ?.firstOrNull { it.platform == primary.ref.platform }
                ?.nameCnOverride
                ?: identityRecord?.nameCn,
            summary = identityRecord?.intro?.takeIf(String::isNotBlank) ?: primary.summary,
            mcmodIconUrl = identityRecord?.logoUrl,
            downloadCount = sumOf(CatalogProjectSource::downloadCount),
            updatedAt = maxOf(CatalogProjectSource::updatedAt),
            environment = primary.environment
        )
    }

    private suspend fun loadProject(ref: CatalogProjectRef): CatalogProjectSource? =
        projectCache.getOrLoad(ref) {
            adapters.getValue(ref.platform).getProjects(setOf(ref.projectId)).found[ref.projectId]
        }

    private suspend fun identityForFile(file: CatalogFile): CatalogIdentity? =
        loadProject(file.project)?.let { identityFor(it).first }

    private suspend fun resolveDependency(
        target: DependencyTarget,
        catalogTarget: CatalogTarget,
        channels: Set<ReleaseChannel>
    ): CatalogFile? = when (target) {
        is DependencyTarget.File -> fileCache.getOrLoad(target.ref) {
            adapters.getValue(target.ref.platform).getFiles(setOf(target.ref.fileId)).found[target.ref.fileId]
        }

        is DependencyTarget.Project -> adapters.getValue(target.ref.platform).listFiles(
            target.ref,
            catalogTarget,
            channels,
            0,
            1
        ).first.firstOrNull()
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

    private fun CatalogSort.comparator(): Comparator<CatalogMod> = when (this) {
        CatalogSort.RELEVANCE -> compareBy<CatalogMod> {
            if (it.primaryRef.platform == ModPlatform.MODRINTH) 0 else 1
        }.thenByDescending(CatalogMod::downloadCount)

        CatalogSort.DOWNLOADS -> compareByDescending(CatalogMod::downloadCount)
        CatalogSort.UPDATED -> compareByDescending(CatalogMod::updatedAt)
    }

    private data class Traversal(
        val file: CatalogFile,
        val path: List<CatalogFileRef>,
        val depth: Int
    )

    private sealed interface LocalProjectCall {
        data class Found(val source: CatalogProjectSource?) : LocalProjectCall
        data class Failed(val platform: ModPlatform, val cause: Throwable) : LocalProjectCall
    }

    private sealed interface SourceCall<out T> {
        val platform: ModPlatform

        data class Success<T>(override val platform: ModPlatform, val value: T) : SourceCall<T>

        data class Failure(override val platform: ModPlatform, val cause: Throwable) : SourceCall<Nothing>
    }

    companion object {
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
