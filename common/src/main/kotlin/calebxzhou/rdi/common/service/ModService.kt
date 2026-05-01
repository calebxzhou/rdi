package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.humanSpeed
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.deser
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.net.DownloadProgress
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.Toml
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.io.path.exists


object ModService {
    var preferMirror = true
    val briefInfo: List<ModBriefInfo> by lazy { loadBriefInfo() }
    private val nameSearchIgnoredChars = setOf(
        ' ', '\t', '\r', '\n',
        '[', ']', '【', '】', '(', ')', '（', '）',
        '{', '}', '<', '>', '《', '》', '「', '」', '『', '』',
        ':', '：', '-', '_'
    )
    const val NEOFORGE_CONFIG_PATH = "META-INF/neoforge.mods.toml"
    const val FABRIC_CONFIG_PATH = "fabric.mod.json"
    const val FORGE_CONFIG_PATH = "META-INF/mods.toml"
    const val LEGACY_FORGE_CONFIG_PATH = "mcmod.info"
    private val lgr by Loggers
    private val modsToml = Toml { ignoreUnknownKeys = true }
    private val supportedModsTomlPaths = listOf(NEOFORGE_CONFIG_PATH, FORGE_CONFIG_PATH)

    val downloadedMods = DL_MOD_DIR.listFiles { it.extension == "jar" }?.toMutableList() ?: mutableListOf()
    var installedMods = DL_MOD_DIR.listFiles { it.extension == "jar" }?.toMutableList() ?: mutableListOf()

    fun resolveModrinthSlugsByChineseName(query: String, maxResults: Int = 5): List<String> =
        resolveRemoteSlugsByChineseName(query, maxResults) { it.modrinthSlugs }

    fun resolveCurseForgeSlugsByChineseName(query: String, maxResults: Int = 5): List<String> =
        resolveRemoteSlugsByChineseName(query, maxResults) { it.curseforgeSlugs }

    private fun resolveRemoteSlugsByChineseName(
        query: String,
        maxResults: Int,
        slugSelector: (ModBriefInfo) -> List<String>
    ): List<String> {
        val normalizedQuery = query.normalizeModSearchName()
        if (normalizedQuery.isBlank() || query.none { it.isCjkChar() }) return emptyList()

        return briefInfo.asSequence()
            .mapIndexedNotNull { index, info ->
                val slugs = slugSelector(info)
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .toList()
                if (slugs.isEmpty()) return@mapIndexedNotNull null

                val nameCnCandidates = info.nameCn
                    ?.let { listOf(it, it.withoutLeadingModNameTags()) }
                    .orEmpty()
                    .map { it.normalizeModSearchName() }
                    .filter(String::isNotBlank)
                    .distinct()
                val nameCandidates = listOf(info.name, info.name.withoutLeadingModNameTags())
                    .map { it.normalizeModSearchName() }
                    .filter(String::isNotBlank)
                    .distinct()
                val match = bestRemoteSlugNameMatch(normalizedQuery, nameCnCandidates, nameCandidates)
                    ?: return@mapIndexedNotNull null
                RemoteSlugNameMatch(slugs, match.score, match.nameLength, index)
            }
            .sortedWith(
                compareBy<RemoteSlugNameMatch> { it.score }
                    .thenBy { it.nameLength }
                    .thenBy { it.index }
            )
            .flatMap { it.slugs }
            .distinct()
            .take(maxResults)
            .toList()
    }

    private data class RemoteSlugNameMatch(
        val slugs: List<String>,
        val score: Int,
        val nameLength: Int,
        val index: Int
    )

    private data class NameMatchScore(
        val score: Int,
        val nameLength: Int
    )

    private fun bestRemoteSlugNameMatch(
        query: String,
        nameCnCandidates: List<String>,
        nameCandidates: List<String>
    ): NameMatchScore? =
        (nameCnCandidates.mapNotNull { name ->
            when {
                name == query -> NameMatchScore(0, name.length)
                name.startsWith(query) -> NameMatchScore(1, name.length)
                name.contains(query) -> NameMatchScore(2, name.length)
                else -> null
            }
        } + nameCandidates.mapNotNull { name ->
            if (name.contains(query)) NameMatchScore(3, name.length) else null
        }).minWithOrNull(compareBy<NameMatchScore> { it.score }.thenBy { it.nameLength })

    private fun String.normalizeModSearchName(): String =
        buildString(length) {
            this@normalizeModSearchName.lowercase().forEach { char ->
                if (char !in nameSearchIgnoredChars) append(char)
            }
        }

    private fun String.withoutLeadingModNameTags(): String {
        var value = trim()
        listOf('[' to ']', '【' to '】', '(' to ')', '（' to '）').forEach { (open, close) ->
            if (value.startsWith(open)) {
                val closeIndex = value.indexOf(close)
                if (closeIndex in 1..16) value = value.substring(closeIndex + 1).trim()
            }
        }
        return value
    }

    private fun Char.isCjkChar(): Boolean = this in '\u4e00'..'\u9fff'

    fun JarFile.readNeoForgeConfig(): ModsTomlConfig? {
        return supportedModsTomlPaths.firstNotNullOfOrNull(::getJarEntry)?.let { modsTomlEntry ->
            getInputStream(modsTomlEntry).bufferedReader().use { reader ->
                parseModsToml(reader.readText())
            }
        }
    }

    fun JarFile.readModMeta(): JarModMeta? {
        readNeoForgeConfig()?.let { config ->
            val modEntries = config.mods
            val modIds = modEntries.mapNotNull { entry ->
                entry.modId.trim().lowercase().ifBlank { null }
            }.distinct()
            if (modIds.isNotEmpty()) {
                if (modIds.size > 1) {
                    lgr.warn { "Jar ${name}声明了多个modId: ${modIds.joinToString()}，将使用第一个${modIds.first()}" }
                }
                val primary = modEntries.firstOrNull { it.modId.isNotBlank() }
                return JarModMeta(
                    modIds = modIds,
                    version = primary?.version?.trim()?.ifBlank { null },
                    description = primary?.description?.trim()?.ifBlank { null }
                )
            }
        }
        return readLegacyForgeModMeta()
    }

    val ModsTomlConfig.modId
        get() = mods.firstOrNull()?.modId.orEmpty()
    val ModsTomlConfig.modDescription
        get() = mods.firstOrNull()?.description.orEmpty()
    val JarFile.modLogo
        get() =
            getJarEntry("logo.png")?.let { logoEntry ->
                getInputStream(logoEntry).readBytes()
            }

    private val builtinDependencyIds = setOf("minecraft", "forge", "neoforge", "fabricloader")

    fun ModBriefInfo.toVo(modFile: File? = null): Mod.CardVo {
        val iconBytes = modFile?.let {
            runCatching { JarFile(it).use { jar -> jar.modLogo } }.getOrNull()
        }
        return Mod.CardVo(
            name = name,
            nameCn = nameCn,
            intro = intro,
            iconData = iconBytes,
            iconUrls = buildList {
                if (logoUrl.isNotBlank()) add(logoUrl)
            },
            side = Mod.Side.BOTH
        )
    }

    fun List<File>.filterServerOnlyMods() =
        filterNot { file ->
            JarFile(file).use { jar ->
                val config = jar.readNeoForgeConfig() ?: return@use false
                config.mods.any { modConfig ->
                    val modId = modConfig.modId.trim().lowercase().ifEmpty { return@any false }
                    extractModsTomlDependencies(config, modId).any { dependency ->
                        val dependencyModId = dependency.modId.trim().lowercase()
                        val side = dependency.side?.trim()?.uppercase()
                        dependencyModId == "minecraft" && side == "CLIENT"
                    }
                }
            }
        }.toMutableList()

    data class UnmatchedDependencies(
        val modId: String,
        val missing: List<Missing>
    ) {
        data class Missing(val modId: String, val version: String? = null)
    }

    fun List<File>.checkDependencies(): List<UnmatchedDependencies> {
        val installedModIds = mutableSetOf<String>()
        this.forEach { file ->
            runCatching {
                JarFile(file).use { jar ->
                    collectModIdsFromJar(jar, installedModIds)
                }
            }.onFailure { err ->
                lgr.error { "Failed to read mod id from file: ${file.name + "\n" + err}" }
            }
        }

        val unmatched = mutableListOf<UnmatchedDependencies>()

        this.forEach { file ->
            runCatching {
                JarFile(file).use { jar ->
                    val config = jar.readNeoForgeConfig() ?: return@use null
                    config.mods.forEach { modConfig ->
                        val modId = modConfig.modId.trim().lowercase().ifEmpty { return@forEach }
                        val missingDependencies = extractModsTomlDependencies(config, modId).mapNotNull { dependency ->
                            val dependencyModId = dependency.modId.trim().lowercase().ifEmpty { return@mapNotNull null }
                            if (dependencyModId.isEmpty() || builtinDependencyIds.contains(dependencyModId)) return@mapNotNull null

                            val side = dependency.side?.trim()?.uppercase()
                            if (side == "CLIENT") return@mapNotNull null

                            val dependencyType = dependency.type?.trim()?.lowercase()
                            val optional = dependency.optional
                            val mandatory = dependency.mandatory
                            val required = when {
                                dependencyType == "optional" -> false
                                dependencyType == "incompatible" -> false
                                mandatory != null -> mandatory
                                optional -> false
                                dependencyType == "required" -> true
                                dependencyType == "discouraged" -> false
                                dependencyType == null -> true

                                else -> true
                            }
                            if (!required) return@mapNotNull null

                            if (installedModIds.contains(dependencyModId)) return@mapNotNull null

                            val versionRange = dependency.versionRange?.takeIf { it.isNotBlank() }
                            UnmatchedDependencies.Missing(dependencyModId, versionRange)
                        }
                            .distinctBy { it.modId }
                        if (missingDependencies.isNotEmpty()) {
                            lgr.warn {
                                "Mod '$modId' is missing dependencies: ${
                                    missingDependencies.joinToString(", ") { missing ->
                                        missing.version?.let { ver -> "${missing.modId} ($ver)" } ?: missing.modId
                                    }
                                }"
                            }
                            unmatched += UnmatchedDependencies(modId, missingDependencies)
                        }
                    }
                }
            }.onFailure { err ->
                lgr.error { "Failed to check dependencies for mod file: ${file.name + "\n" + err}" }
            }
        }
        return unmatched
    }

    private fun collectModIdsFromJar(jar: JarFile, installedModIds: MutableSet<String>) {
        jar.readModMeta()?.let { meta ->
            installedModIds += meta.modIds
        }

        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name
            if (!name.startsWith("META-INF/jarjar/", ignoreCase = true)) continue
            if (!name.endsWith(".jar", ignoreCase = true)) continue

            runCatching {
                jar.getInputStream(entry).use { nestedInput ->
                    collectModIdsFromNestedJar(nestedInput, installedModIds)
                }
            }.onFailure { err ->
                lgr.warn { "Failed to inspect nested jar '$name' inside ${jar.name}" }
                err.printStackTrace()
            }
        }
    }

    private fun collectModIdsFromNestedJar(inputStream: InputStream, installedModIds: MutableSet<String>) {
        JarInputStream(inputStream).use { nestedJar ->
            var entry = nestedJar.nextJarEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    when {
                        supportedModsTomlPaths.any { it.equals(entryName, ignoreCase = false) } -> {
                            val configText = nestedJar.readBytes().toString(Charsets.UTF_8)
                            parseModsToml(configText)?.let { config ->
                                installedModIds += extractModIds(config)
                            }
                        }

                        entryName.startsWith("META-INF/jarjar/", ignoreCase = true) &&
                                entryName.endsWith(".jar", ignoreCase = true) -> {
                            val nestedBytes = nestedJar.readBytes()
                            collectModIdsFromNestedJar(ByteArrayInputStream(nestedBytes), installedModIds)
                        }
                    }
                }
                nestedJar.closeEntry()
                entry = nestedJar.nextJarEntry
            }
        }
    }

    private fun parseModsToml(raw: String): ModsTomlConfig? {
        if (raw.isBlank()) return null
        return runCatching {
            modsToml.decodeFromString<ModsTomlConfig>(raw)
        }.onFailure { err ->
            lgr.debug(err) { "Failed to parse mods.toml" }
        }.getOrNull()
    }

    private fun JarFile.readLegacyForgeModMeta(): JarModMeta? {
        val mcmodInfoEntry = getJarEntry(LEGACY_FORGE_CONFIG_PATH) ?: return null
        val entries = getInputStream(mcmodInfoEntry).bufferedReader().use { reader ->
            parseLegacyForgeModEntries(reader.readText())
        }
        if (entries.isEmpty()) return null
        val modIds = entries.mapNotNull { entry ->
            entry.modId.trim().lowercase().ifBlank { null }
        }.distinct()
        if (modIds.isEmpty()) return null
        if (modIds.size > 1) {
            lgr.warn { "Jar ${name}声明了多个legacy modId: ${modIds.joinToString()}，将使用第一个${modIds.first()}" }
        }
        val primary = entries.firstOrNull { it.modId.isNotBlank() }
        return JarModMeta(
            modIds = modIds,
            version = primary?.version?.trim()?.ifBlank { null },
            description = primary?.description?.trim()?.ifBlank { null }
        )
    }

    private fun parseLegacyForgeModEntries(raw: String): List<LegacyMcmodInfoEntry> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        return when {
            trimmed.startsWith("[") -> raw.deser<List<LegacyMcmodInfoEntry>>().getOrElse { err ->
                lgr.debug(err) { "Failed to parse legacy mcmod.info array" }
                emptyList()
            }

            trimmed.startsWith("{") -> raw.deser<LegacyMcmodInfoContainer>().map { it.modList }.getOrElse { err ->
                lgr.debug(err) { "Failed to parse legacy mcmod.info object" }
                emptyList()
            }

            else -> emptyList()
        }
    }

    private fun extractModIds(config: ModsTomlConfig?): List<String> {
        return config?.mods.orEmpty()
            .mapNotNull { modConfig ->
                modConfig.modId
                    .trim()
                    .lowercase()
                    .takeIf { it.isNotEmpty() }
            }
    }

    private fun extractModsTomlDependencies(config: ModsTomlConfig, modId: String): List<ModsTomlDependency> {
        val normalizedModId = modId.trim()
        if (normalizedModId.isEmpty()) return emptyList()
        return config.dependencies.entries
            .firstOrNull { (key, _) -> key.equals(normalizedModId, ignoreCase = true) }
            ?.value
            .orEmpty()
    }


    val String.ofMirrorUrl
        get() = this.replace("edge.forgecdn.net", "mod.mcimirror.top")
            .replace("mediafilez.forgecdn.net", "mod.mcimirror.top")
            .replace("media.forgecdn.net", "mod.mcimirror.top")
            .replace("api.modrinth.com", "mod.mcimirror.top/modrinth")
            .replace("staging-api.modrinth.com", "mod.mcimirror.top/modrinth")
            .replace("cdn.modrinth.com", "mod.mcimirror.top")
            .replace("api.curseforge.com", "mod.mcimirror.top/curseforge")

    private fun readResourceText(resourcePath: String): String? {
        val classLoaders = listOfNotNull(
            ModService::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
            ClassLoader.getSystemClassLoader()
        ).distinct()

        val resourceCandidates = listOf(
            resourcePath,
            "/$resourcePath",
            "assets/$resourcePath",
            "resources/$resourcePath"
        )

        classLoaders.forEach { loader ->
            resourceCandidates.forEach { candidate ->
                val normalized = candidate.removePrefix("/")
                val text = runCatching {
                    loader.getResourceAsStream(normalized)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (!text.isNullOrBlank()) return text
            }
        }

        val fileCandidates = listOf(
            File(resourcePath),
            File("common/src/main/resources/$resourcePath"),
            File("client/ui/src/commonMain/resources/$resourcePath")
        )
        fileCandidates.forEach { file ->
            val text = runCatching {
                if (file.exists()) file.readText() else null
            }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    fun loadBriefInfo(): List<ModBriefInfo> {
        val resourcePath = "mod_brief_info.json"
        val raw = runCatching {
            readResourceText(resourcePath)
        }.onFailure {
            lgr.error { "Failed to read $resourcePath" + "\n" + it }
        }.getOrNull()

        if (raw.isNullOrBlank()) {
            lgr.warn { "mod_brief_info.json is missing or empty; fallback to empty brief info list" }
            return emptyList()
        }

        return runCatching { serdesJson.decodeFromString<List<ModBriefInfo>>(raw) }
            .onFailure { err -> lgr.error { "Failed to decode mod_brief_info.json" + "\n" + err } }
            .getOrElse { emptyList() }
    }

    fun buildSlugMap(
        data: List<ModBriefInfo>,
        slugSelector: (ModBriefInfo) -> List<String>
    ): Map<String, ModBriefInfo> {
        val map = linkedMapOf<String, ModBriefInfo>()
        data.forEach { info ->
            slugSelector(info)
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { slug ->
                    val normalized = slug.lowercase()
                    val previous = map.put(normalized, info)
                    if (previous != null && previous !== info) {
                        lgr.debug { "Duplicated slug '$slug' now mapped to ${info.mcmodId}, previously ${previous.mcmodId}" }
                    }
                }
        }
        return map
    }

    fun downloadModsTask2(mods: List<Mod>): Task2 {
        if (mods.isEmpty()) return Task2.Group("下载Mod", emptyList())
        val cfMods = mods.filter { it.platform == "cf" }
        val mrMods = mods.filter { it.platform == "mr" }
        val githubMods = mods.filter { it.platform == "github" }
        return Task2.Sequence(
            title = "下载${mods.size}个Mod",
            children = buildList {
                add(downloadCFModsTask2(cfMods))
                add(downloadMRModsTask2(mrMods))
                add(downloadGithubModsTask2(githubMods))
            }
        )
    }

    fun isDownloadedModFileValid(mod: Mod): Boolean {
        val targetPath = mod.targetPath
        if (!targetPath.exists()) return false

        val expectedHash = mod.hash.trim().lowercase()
        return runCatching {
            when (mod.platform.lowercase()) {
                "cf" -> {
                    val expectedFingerprint = expectedHash.toLongOrNull()
                    expectedFingerprint != null && targetPath.murmur2 == expectedFingerprint
                }

                "mr", "github" -> targetPath.sha1 == expectedHash
                else -> true
            }
        }.getOrDefault(false)
    }

    fun downloadCFModsTask2(mods: List<Mod>): Task2 {
        if (mods.isEmpty()) return Task2.Group("下载CurseForge Mod", emptyList())
        val fileIds = mods.map { it.fileId.toInt() }
        val fileInfoMap = mutableMapOf<Int, CurseForgeFile>()
        val aggregateProgress = createBatchProgressTracker2(mods)
        val prepareTask = Task2.Leaf("获取CurseForge文件信息") { ctx ->
            val fileInfos = CurseForgeService.getModFilesInfo(fileIds)
            fileInfoMap.clear()
            fileInfoMap.putAll(fileInfos.associateBy { it.id })
            ctx.emit(Task2Progress("获取完成", 1f))
        }
        val tasks = mods.map { mod ->
            Task2.Leaf("下载 ${mod.slug}") { ctx ->
                val fileInfo = fileInfoMap[mod.fileId.toInt()]
                    ?: throw IllegalStateException("未找到文件信息: ${mod.slug}")
                val result = downloadSingleCFMod(mod, fileInfo) { progress ->
                    ctx.emit(aggregateProgress(mod, progress))
                }
                result.getOrElse { throw it }
                ctx.emit(aggregateProgress(mod, DownloadProgress(1, 1, 0.0)))
            }
        }
        return Task2.Sequence(
            title = "下载CurseForge Mod",
            children = listOf(
                prepareTask,
                Task2.Group("下载CurseForge Mod", tasks)
            )
        )
    }

    fun downloadMRModsTask2(mods: List<Mod>): Task2 {
        if (mods.isEmpty()) return Task2.Group("下载Modrinth Mod", emptyList())
        val modsWithUrls = mods.filter { it.downloadUrls.isNotEmpty() }
        val aggregateProgress = createBatchProgressTracker2(modsWithUrls)
        val tasks = modsWithUrls.map { mod ->
            Task2.Leaf("下载 ${mod.slug}") { ctx ->
                val result = downloadSingleMRMod(mod) { progress ->
                    ctx.emit(aggregateProgress(mod, progress))
                }
                result.getOrElse { throw it }
                ctx.emit(aggregateProgress(mod, DownloadProgress(1, 1, 0.0)))
            }
        }
        return Task2.Group("下载Modrinth Mod", tasks)
    }

    fun downloadGithubModsTask2(mods: List<Mod>): Task2 {
        if (mods.isEmpty()) return Task2.Group("下载GitHub Mod", emptyList())
        val modsWithUrls = mods.filter { it.downloadUrls.isNotEmpty() }
        val aggregateProgress = createBatchProgressTracker2(modsWithUrls)
        val tasks = modsWithUrls.map { mod ->
            Task2.Leaf("下载 ${mod.slug}") { ctx ->
                val result = downloadSingleMRMod(mod) { progress ->
                    ctx.emit(aggregateProgress(mod, progress))
                }
                result.getOrElse { throw it }
                ctx.emit(aggregateProgress(mod, DownloadProgress(1, 1, 0.0)))
            }
        }
        return Task2.Group("下载GitHub Mod", tasks)
    }

    private fun createBatchProgressTracker2(mods: List<Mod>): (Mod, DownloadProgress) -> Task2Progress {
        if (mods.isEmpty()) return { mod, _ -> Task2Progress("Mod下载中 ${mod.slug}", 1f) }
        val totalMods = mods.size.toDouble()
        val progressMap = linkedMapOf<String, DownloadProgress>().apply {
            mods.forEach { put(it.batchProgressKey, DownloadProgress(0, 0, 0.0)) }
        }
        val lock = Any()
        return { mod, progress ->
            val snapshot = synchronized(lock) {
                progressMap[mod.batchProgressKey] = progress
                val overallFraction = (
                    progressMap.values.sumOf { it.fraction.coerceIn(0f, 1f).toDouble() } / totalMods
                ).toFloat().coerceIn(0f, 1f)
                val totalSpeed = progressMap.values.sumOf { it.speedBytesPerSecond.coerceAtLeast(0.0) }
                val doneCount = progressMap.values.count { it.fraction >= 1f }
                Triple(overallFraction, totalSpeed, doneCount)
            }
            val (overallFraction, totalSpeed, doneCount) = snapshot
            Task2Progress(
                "Mod下载中 ${doneCount}/${mods.size} ${mod.slug} · 总速度${totalSpeed.humanSpeed}",
                overallFraction
            )
        }
    }

    private val Mod.batchProgressKey: String
        get() = "${platform}:${projectId}:${fileId}:${slug}"

    private suspend fun downloadSingleCFMod(
        mod: Mod,
        fileInfo: CurseForgeFile,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Path> {
        val targetPath = mod.targetPath
        val expectedFingerprint = fileInfo.fileFingerprint
        // Check if file already exists with expected hash/fingerprint.
        if (targetPath.exists()) {
            val alreadyOk = targetPath.murmur2 == expectedFingerprint
            if (alreadyOk) {
                lgr.info { "Mod file already exists and hash matches: $targetPath" }
                return Result.success(targetPath)
            }
        }

        val officialUrls = (mod.downloadUrls + fileInfo.realDownloadUrl)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val mirrorUrls = officialUrls.map { it.ofMirrorUrl }
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it in officialUrls }
            .distinct()
        val primaryUrls = if (preferMirror) mirrorUrls else officialUrls
        val fallbackUrls = if (preferMirror) officialUrls else mirrorUrls
        val allCandidateUrls = primaryUrls + fallbackUrls

        val finalResult = runCatching {
            val downloadedPath = targetPath.downloadFileFrom(
                primaryUrls = primaryUrls,
                fallbackUrls = fallbackUrls,
                onProgress = onProgress
            ).getOrElse { throw it }
            val actualFingerprint = downloadedPath.murmur2
            if (actualFingerprint != expectedFingerprint) {
                throw IllegalStateException(
                    "Downloaded mod ${mod.slug} fingerprint mismatch: expected $expectedFingerprint, got $actualFingerprint"
                )
            }
            downloadedPath
        }.onFailure { err ->
            lgr.warn { "Download failed for ${mod.slug} from ${allCandidateUrls.joinToString()}\n$err" }
        }

        finalResult.onFailure { err ->
            lgr.error { "Failed to download mod ${mod.slug + "\n" + err}" }
        }

        return finalResult
    }

    private suspend fun downloadSingleMRMod(
        mod: Mod,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Path> {
        val targetPath = mod.targetPath

        // Check if file already exists with correct hash
        if (targetPath.exists()) {
            // For MR mods, we use the hash from the mod object
            val expectedHash = mod.hash
            if (expectedHash.isNotBlank()) {
                val actualHash = targetPath.sha1
                if (actualHash == expectedHash) {
                    lgr.debug { "Mod file already exists and hash matches: $targetPath" }
                    return Result.success(targetPath)
                }
            }
        }

        val urls = mod.downloadUrls
        val expectedHash = mod.hash
        val officialUrls = urls
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        val mirrorUrls = officialUrls.map { it.ofMirrorUrl }
            .filter(String::isNotBlank)
            .filterNot { it in officialUrls }
            .distinct()
        val primaryUrls = if (preferMirror) mirrorUrls else officialUrls
        val fallbackUrls = if (preferMirror) officialUrls else mirrorUrls
        val allCandidateUrls = primaryUrls + fallbackUrls

        val result = runCatching {
            val downloadedPath = targetPath.downloadFileFrom(
                primaryUrls = primaryUrls,
                fallbackUrls = fallbackUrls,
                onProgress = onProgress
            ).getOrElse { throw it }

            if (expectedHash.isNotBlank()) {
                val actualHash = downloadedPath.sha1
                if (actualHash != expectedHash) {
                    throw IllegalStateException(
                        "Downloaded mod ${mod.slug} SHA1 mismatch: expected $expectedHash, got $actualHash"
                    )
                }
            }
            downloadedPath
        }.onFailure { err ->
            lgr.warn { "Download failed for ${mod.slug} from ${allCandidateUrls.joinToString()}\n$err" }
        }

        result.onFailure { err ->
            lgr.error { "Failed to download mod ${mod.slug + "\n" + err}" }
        }
        return result
    }

    fun MutableList<Mod>.postProcessModSides(): MutableList<Mod> {
        if (isEmpty()) return this

        val forceBoth = setOf(
            "loot-beams-refork",
            "particular-reforged",
            "inventory-profiles-next",
            "inventory-tweaks-refoxed",
            "just-enough-resources-jer",
            "radiant-gear",
            "fusion-connected-textures",
            "apothic-attributes",
            "the-twilight-forest",
        )
        val forceClient = setOf(
            "status-effect-bars-reforged",
            "mafglib",
            "flighthud-reborn",
            "i18nupdatemod",
            "modern-ui",
            "controllable",
            "reforgedplay-mod"
        )

        forEach { mod ->
            val slug = mod.slug.trim().lowercase()
            when {
                slug.startsWith("ftb") -> mod.side = Mod.Side.BOTH
                slug in forceBoth -> mod.side = Mod.Side.BOTH
                slug in forceClient -> mod.side = Mod.Side.CLIENT
            }
            mod.vo = mod.vo?.copy(side = mod.side)
        }
        return this
    }
}



