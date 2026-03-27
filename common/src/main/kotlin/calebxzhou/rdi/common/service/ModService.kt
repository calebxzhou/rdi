package calebxzhou.rdi.common.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.net.DownloadProgress
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.decodeFromString
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import net.peanuuutz.tomlkt.Toml


object ModService {
    var useMirror = true
    val briefInfo: List<ModBriefInfo> by lazy { loadBriefInfo() }
    const val NEOFORGE_CONFIG_PATH = "META-INF/neoforge.mods.toml"
    const val FABRIC_CONFIG_PATH = "fabric.mod.json"
    const val FORGE_CONFIG_PATH = "META-INF/mods.toml"
    private val lgr by Loggers
    private val modsToml = Toml { ignoreUnknownKeys = true }
    private val supportedModsTomlPaths = listOf(NEOFORGE_CONFIG_PATH, FORGE_CONFIG_PATH)

    val downloadedMods = DL_MOD_DIR.listFiles { it.extension == "jar" }?.toMutableList() ?: mutableListOf()
    var installedMods = DL_MOD_DIR.listFiles { it.extension == "jar" }?.toMutableList() ?: mutableListOf()
    fun JarFile.readNeoForgeConfig(): ModsTomlConfig? {
        return supportedModsTomlPaths.firstNotNullOfOrNull(::getJarEntry)?.let { modsTomlEntry ->
            getInputStream(modsTomlEntry).bufferedReader().use { reader ->
                parseModsToml(reader.readText())
            }
        }
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
                lgr.error { "Failed to read mod id from file: ${file.name + "\n" + err }" }
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
                lgr.error { "Failed to check dependencies for mod file: ${file.name + "\n" + err }" }
            }
        }
        return unmatched
    }

    private fun collectModIdsFromJar(jar: JarFile, installedModIds: MutableSet<String>) {
        jar.readNeoForgeConfig()?.let { config ->
            installedModIds += extractModIds(config)
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

    fun downloadModsTask(mods: List<Mod>): Task {
        if (mods.isEmpty()) return Task.Group("下载Mod", emptyList())
        val cfMods = mods.filter { it.platform == "cf" }
        val mrMods = mods.filter { it.platform == "mr" }
        val tasks = buildList {
            add(downloadCFModsTask(cfMods))
            add(downloadMRModsTask(mrMods))
        }
        return Task.Sequence("下载${mods.size}个Mod", tasks)
    }

    fun isDownloadedModFileValid(mod: Mod): Boolean {
        val targetPath = mod.targetPath
        if (!targetPath.exists()) return false

        val expectedHash = mod.hash.trim().lowercase()
        return runCatching {
            when (mod.platform.lowercase()) {
                "cf" -> {
                    val expectedFingerprint = expectedHash.toLongOrNull()
                    expectedFingerprint != null && targetPath.murmur2Hash() == expectedFingerprint
                }

                "mr" -> targetPath.sha1Hex() == expectedHash
                else -> true
            }
        }.getOrDefault(false)
    }

    fun downloadCFModsTask(mods: List<Mod>): Task {
        if (mods.isEmpty()) return Task.Group("下载CurseForge Mod", emptyList())
        val fileIds = mods.map { it.fileId.toInt() }
        val fileInfoMap = mutableMapOf<Int, CurseForgeFile>()
        val prepareTask = Task.Leaf("获取CurseForge文件信息") { ctx ->
            val fileInfos = CurseForgeService.getModFilesInfo(fileIds)
            fileInfoMap.clear()
            fileInfoMap.putAll(fileInfos.associateBy { it.id })
            ctx.emitProgress(TaskProgress("获取完成", 1f))
        }
        val tasks = mods.map { mod ->
            Task.Leaf("下载 ${mod.slug}") { ctx ->
                val fileInfo = fileInfoMap[mod.fileId.toInt()]
                    ?: throw IllegalStateException("未找到文件信息: ${mod.slug}")
                val result = downloadSingleCFMod(mod, fileInfo, 4) { progress ->
                    ctx.emitProgress(
                        TaskProgress(
                            "Mod下载中 ${mod.slug}",
                            progress.fraction.coerceIn(0f, 1f)
                        )
                    )
                }
                result.getOrElse { throw it }
            }
        }
        return Task.Sequence("下载CurseForge Mod", listOf(prepareTask, Task.Group("下载CurseForge Mod", tasks)))
    }

    fun downloadMRModsTask(mods: List<Mod>): Task {
        if (mods.isEmpty()) return Task.Group("下载Modrinth Mod", emptyList())
        val modsWithUrls = mods.filter { it.downloadUrls.isNotEmpty() }
        val tasks = modsWithUrls.map { mod ->
            Task.Leaf("下载 ${mod.slug}") { ctx ->
                val result = downloadSingleMRMod(mod) { progress ->
                    ctx.emitProgress(
                        TaskProgress(
                            "Mod下载中 ${mod.slug}",
                            progress.fraction.coerceIn(0f, 1f)
                        )
                    )
                }
                result.getOrElse { throw it }
            }
        }
        return Task.Group("下载Modrinth Mod", tasks)
    }

    private suspend fun downloadSingleCFMod(
        mod: Mod,
        fileInfo: CurseForgeFile,
        rangeParallelism: Int,
        onProgress: (DownloadProgress) -> Unit
    ): Result<Path> {
        val targetPath = mod.targetPath
        val expectedFingerprint = fileInfo.fileFingerprint
        val expectedSha1 = fileInfo.hashes
            .firstOrNull { it.algo == 1 }
            ?.value
            ?.trim()
            ?.lowercase()
            .orEmpty()

        // Check if file already exists with expected hash/fingerprint.
        if (targetPath.exists()) {
            val alreadyOk = if (expectedSha1.isNotBlank()) {
                targetPath.sha1Hex() == expectedSha1
            } else {
                targetPath.murmur2Hash() == expectedFingerprint
            }
            if (alreadyOk) {
                lgr.debug { "Mod file already exists and hash matches: $targetPath" }
                return Result.success(targetPath)
            }
        }

        val officialUrl = fileInfo.realDownloadUrl
        val mirrorUrl = if (useMirror) {
            officialUrl.ofMirrorUrl
        } else {
            officialUrl
        }

        suspend fun attemptDownload(url: String, label: String): Result<Path> = runCatching {
            val downloadedPath = targetPath.downloadFileFrom(
                url,
                onProgress = onProgress
            ).getOrElse { throw it }

            // Prefer SHA1 verification when API provides it; fallback to murmur2 fingerprint.
            if (expectedSha1.isNotBlank()) {
                val actualSha1 = downloadedPath.sha1Hex()
                if (actualSha1 != expectedSha1) {
                    throw IllegalStateException(
                        "Downloaded mod ${mod.slug} SHA1 mismatch: expected $expectedSha1, got $actualSha1"
                    )
                }
            } else {
                val actualFingerprint = downloadedPath.murmur2Hash()
                if (actualFingerprint != expectedFingerprint) {
                    throw IllegalStateException(
                        "Downloaded mod ${mod.slug} fingerprint mismatch: expected $expectedFingerprint, got $actualFingerprint"
                    )
                }
            }
            downloadedPath
        }.onFailure { err ->
            if (label == "mirror") {
                lgr.warn { "Mirror download failed for ${mod.slug + "\n" + err }, will retry official" }
            }
        }

        // Try mirror first if enabled, then fall back to official
        val mirrorResult = if (useMirror) attemptDownload(mirrorUrl, "mirror") else null
        val finalResult = when {
            mirrorResult == null -> attemptDownload(officialUrl, "official")
            mirrorResult.isSuccess -> mirrorResult
            else -> attemptDownload(officialUrl, "official")
        }

        finalResult.onFailure { err ->
            lgr.error { "Failed to download mod ${mod.slug + "\n" + err }" }
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
                val actualHash = targetPath.sha1Hex()
                if (actualHash == expectedHash) {
                    lgr.debug { "Mod file already exists and hash matches: $targetPath" }
                    return Result.success(targetPath)
                }
            }
        }

        val urls = mod.downloadUrls
        val expectedHash = mod.hash
        var lastError: Throwable? = null

        suspend fun attemptDownload(url: String, label: String): Result<Path> = runCatching {
            val downloadedPath = targetPath.downloadFileFrom(
                url,
                onProgress = onProgress
            ).getOrElse { throw it }

            if (expectedHash.isNotBlank()) {
                val actualHash = downloadedPath.sha1Hex()
                if (actualHash != expectedHash) {
                    throw IllegalStateException(
                        "Downloaded mod ${mod.slug} SHA1 mismatch: expected $expectedHash, got $actualHash"
                    )
                }
            }
            downloadedPath
        }.onFailure { err ->
            if (label == "mirror") {
                lgr.warn { "Mirror download failed for ${mod.slug + "\n" + err }, will retry official" }
            }
        }

        // Try each URL in order until one succeeds
        for ((index, officialUrl) in urls.withIndex()) {
            val candidateUrls = buildList {
                if (useMirror) {
                    val mirrorUrl = officialUrl.ofMirrorUrl
                    if (mirrorUrl != officialUrl) {
                        add("mirror" to mirrorUrl)
                    }
                }
                add("official" to officialUrl)
            }

            var result: Result<Path> = Result.failure(IllegalStateException("No download URL available"))
            for ((label, url) in candidateUrls) {
                result = attemptDownload(url, label)
                if (result.isSuccess) {
                    lgr.debug { "Successfully downloaded ${mod.slug} from $label URL #${index + 1}" }
                    return result
                }
            }

            if (result.isSuccess) {
                return result
            }

            lastError = result.exceptionOrNull()
            lgr.warn { "Download failed for ${mod.slug + "\n" + lastError } from URL #${index + 1}: $officialUrl" }

            if (index < urls.lastIndex) {
                lgr.info { "Trying next URL for ${mod.slug}..." }
            }
        }

        // All URLs failed
        lgr.error { "Failed to download mod ${mod.slug + "\n" + lastError } from all ${urls.size} URLs" }
        return Result.failure(lastError ?: IllegalStateException("No download URLs available"))
    }

    private fun Path.sha1Hex(): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        inputStream().buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.isSha1String(): Boolean =
        length == 40 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    private fun Path.murmur2Hash(seed: Int = 1): Long {
        val m = 0x5bd1e995
        val r = 24
        val length = toFile().length().toInt()
        var h = seed xor length
        val tail = ByteArray(4)
        var tailSize = 0

        fun mixChunk(bytes: ByteArray, offset: Int) {
            var k = (bytes[offset].toInt() and 0xff) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                    ((bytes[offset + 3].toInt() and 0xff) shl 24)
            k *= m
            k = k xor (k ushr r)
            k *= m
            h *= m
            h = h xor k
        }

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        inputStream().buffered().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                var index = 0
                if (tailSize > 0) {
                    while (tailSize < 4 && index < read) {
                        tail[tailSize++] = buffer[index++]
                    }
                    if (tailSize == 4) {
                        mixChunk(tail, 0)
                        tailSize = 0
                    }
                }

                val blockLimit = read - ((read - index) and 3)
                while (index < blockLimit) {
                    mixChunk(buffer, index)
                    index += 4
                }

                while (index < read) {
                    tail[tailSize++] = buffer[index++]
                }
            }
        }

        when (tailSize) {
            3 -> {
                h = h xor ((tail[2].toInt() and 0xff) shl 16)
                h = h xor ((tail[1].toInt() and 0xff) shl 8)
                h = h xor (tail[0].toInt() and 0xff)
                h *= m
            }

            2 -> {
                h = h xor ((tail[1].toInt() and 0xff) shl 8)
                h = h xor (tail[0].toInt() and 0xff)
                h *= m
            }

            1 -> {
                h = h xor (tail[0].toInt() and 0xff)
                h *= m
            }
        }

        h = h xor (h ushr 13)
        h *= m
        h = h xor (h ushr 15)
        return h.toLong() and 0xffffffffL
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
            "controllable"
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



