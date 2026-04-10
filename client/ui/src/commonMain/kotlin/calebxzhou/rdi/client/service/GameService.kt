package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.*
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.model.*
import calebxzhou.rdi.client.ui.loadResourceStream
import calebxzhou.rdi.client.ui.exportResource
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.model.LibraryOsArch.Companion.detectHostOs
import calebxzhou.rdi.common.net.DownloadProgress
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.runInline
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.ZipFile

object GameService {
    private val lgr by Loggers
    var started = false
    var serverStarted = false

    private val libsDir get() = ClientDirs.librariesDir
    private val assetsDir get() = ClientDirs.assetsDir
    private val assetIndexesDir get() = ClientDirs.assetIndexesDir
    private val assetObjectsDir get() = ClientDirs.assetObjectsDir
    val versionListDir get() = ClientDirs.versionsDir

    private val hostOs = detectHostOs()
    private val hostOsArchRaw = System.getProperty("os.arch")?.lowercase(Locale.ROOT) ?: ""
    private val hostOsVersionRaw = System.getProperty("os.version") ?: ""
    private val launcherFeatures: Map<String, Boolean> = emptyMap()
    private val locale = Locale.SIMPLIFIED_CHINESE
    private val mirrors = mapOf(
        "https://maven.neoforged.net/releases" to "https://bmclapi2.bangbang93.com/maven",
        "https://files.minecraftforge.net/maven" to "https://bmclapi2.bangbang93.com/maven",
        "http://launchermeta.mojang.com/mc/game/version_manifest.json" to "https://bmclapi2.bangbang93.com/mc/game/version_manifest.json",
        "http://launchermeta.mojang.com/mc/game/version_manifest_v2.json" to "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json",
        "https://launchermeta.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://launcher.mojang.com" to "https://bmclapi2.bangbang93.com",
        "https://resources.download.minecraft.net" to "https://bmclapi2.bangbang93.com/assets",
        "https://libraries.minecraft.net" to "https://bmclapi2.bangbang93.com/maven",
        "https://maven.minecraftforge.net" to "https://bmclapi2.bangbang93.com/maven",
    )
    private val bracketedLibraryRegex = Regex("^\\[(.+)]$")
    private val numberedAssetRegex = Regex("^(.+?)(\\d+)(\\.[^./]+)$")
    internal val String.rewriteMirrorUrl: String
        get() {
            if (!CONF.useMirror) return this
            val original = this
            mirrors.forEach { (originRaw, mirrorRaw) ->
                val origin = originRaw.trim()
                if (origin.isEmpty()) return@forEach
                if (original.startsWith(origin)) {
                    val suffix = original.removePrefix(origin)
                    val mirror = mirrorRaw.trim()
                    if (suffix.isEmpty()) return mirror
                    val normalizedSuffix = suffix.trimStart('/')
                    val normalizedMirror = mirror.trimEnd('/')
                    return "$normalizedMirror/$normalizedSuffix"
                }
            }
            return original
        }
    fun downloadVersionTask2(version: McVersion, loader: ModLoader? = null): Task2 {
        val manifest = version.metadata
        val tasks = mutableListOf<Task2>(
            downloadClientTask2(manifest),
            downloadLibrariesTask2(manifest.libraries),
            Task2.Leaf("提取原生库") { ctx ->
                extractNatives(manifest) { message ->
                    ctx.emit(Task2Progress(message))
                }
                ctx.emit(Task2Progress("完成", 1f))
            },
            downloadAssetsTask2(manifest)
        )
        loader?.let { tasks += downloadLoaderTask2(version, it) }
        return Task2.Sequence(
            title = "下载 $version",
            children = tasks
        )
    }

    private fun extractNatives(manifest: MojangVersionManifest, onProgress: (String) -> Unit) {
        val nativesDir = versionListDir.resolve(manifest.id).resolve("natives").apply { mkdirs() }
        manifest.libraries.filterNativeOnly.forEach { library ->
            val jarFile = library.nativeArtifact()?.path?.let { path ->
                File(libsDir, path)
            } ?: library.file
            if (!jarFile.exists()) {
                onProgress("运行库${library.name}下载失败，无法提取")
                return@forEach
            }
            onProgress("提取 ${library.name} 的原生库")
            ZipFile(jarFile).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.isNativeLibraryName() }
                    .forEach { entry ->
                        val target = nativesDir.resolve(entry.name.substringAfterLast('/'))
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
            }
        }
    }

    private fun String.isNativeLibraryName(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")
    }


    fun downloadClientTask2(manifest: MojangVersionManifest): Task2 {
        return Task2.Leaf("下载客户端 ${manifest.id}") { ctx ->
            var clientArtf = manifest.downloads?.client ?: run {
                ctx.emit(Task2Progress("缺少客户端下载信息", 0f))
                return@Leaf
            }
            if (CONF.useMirror) {
                clientArtf = MojangDownloadArtifact(
                    url = "https://bmclapi2.bangbang93.com/version/${manifest.id}/client",
                    sha1 = clientArtf.sha1,
                    size = clientArtf.size,
                    path = clientArtf.path
                )
            }
            val versionDir = versionListDir.resolve(manifest.id).apply { mkdirs() }
            File(versionDir, "${manifest.id}.json").writeText(manifest.json)
            val target = File(versionDir, "${manifest.id}.jar")
            ctx.emit(Task2Progress("开始下载...", 0.1f))
            downloadArtifact("客户端核心 ${manifest.id}", clientArtf, target) { progress ->
                ctx.emit(
                    Task2Progress(
                        "${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                        progress.fraction
                    )
                )
            }.getOrThrow()
            ctx.emit(Task2Progress("下载完成", 1f))
        }
    }

    private suspend fun downloadServerTask2(
        holder: LoaderInstallHolder,
        ctx: Task2Context
    ) {
        val mcVerStr = holder.version.mcVer
        var server = holder.version.metadata.downloads?.server ?: run {
            ctx.emit(Task2Progress("缺少服务端下载信息", 0f))
            return
        }
        if (CONF.useMirror) {
            server = MojangDownloadArtifact(
                url = "https://bmclapi2.bangbang93.com/version/${mcVerStr}/server",
                sha1 = server.sha1,
                size = server.size,
                path = server.path
            )
        }
        val serverTargetFile = holder.installProfile?.serverJarPath
            ?.replace("{LIBRARY_DIR}", libsDir.absolutePath)
            ?.replace("{MINECRAFT_VERSION}", mcVerStr)
            ?.let { File(it).apply { parentFile?.mkdirs() } }
            ?: ClientDirs.mcDir.resolve("minecraft_server.${mcVerStr}.jar")
        ctx.emit(Task2Progress("开始下载...", 0.1f))
        downloadArtifact("服务端核心 $mcVerStr", server, serverTargetFile) { progress ->
            ctx.emit(
                Task2Progress(
                    "${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                    progress.fraction
                )
            )
        }.getOrThrow()
        ctx.emit(Task2Progress("下载完成", 1f))
    }

    private fun MojangLibrary.shouldDownloadByArch(): Boolean {
        return rulesAllow(rules)
    }

    private fun MojangLibrary.nativeClassifierKey(): String? {
        return natives?.get(hostOs.ruleOsName)
    }

    private fun MojangLibrary.nativeArtifact(): MojangDownloadArtifact? {
        val key = nativeClassifierKey() ?: return null
        return downloads.classifiers?.get(key)
    }

    private val List<MojangLibrary>.filterNativeOnly
        get() = this.filter { it.nativeArtifact() != null }
            .filter { it.shouldDownloadByArch() }
    val MojangLibrary.file get() = File(libsDir, this.downloads.artifact.path!!)


    private suspend fun downloadLibraryArtifact(
        library: MojangLibrary,
        installer: File? = null,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        val artifact = library.downloads.artifact
        val relativePath = artifact.path ?: return Result.failure(IllegalStateException("缺少库路径"))
        val target = File(libsDir, relativePath)
        return downloadLibraryArtifact(artifact, target, installer, onProgress)
    }

    private suspend fun downloadLibraryArtifact(
        artifact: MojangDownloadArtifact,
        target: File,
        installer: File? = null,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        if (target.exists()) {
            val existingSha = runCatching { target.sha1 }.getOrNull()
            if (existingSha != null && existingSha.equals(artifact.sha1, true)) {
                return Result.success(target)
            }
        }
        target.parentFile?.mkdirs()

        val rawUrl = artifact.url.trim()
        if (rawUrl.isEmpty()) {
            val extracted = tryExtractLibraryFromInstaller(installer, artifact, target)
            if (extracted) {
                val extractedSha = runCatching { target.sha1 }.getOrNull()
                if (extractedSha != null && extractedSha.equals(artifact.sha1, true)) {
                    onProgress(DownloadProgress(target.length(), target.length(), 0.0))
                    return Result.success(target)
                }
                target.delete()
            }
        }

        val resolvedUrl = resolveArtifactUrl(artifact)
        if (resolvedUrl.isBlank()) {
            throw IllegalStateException("${target.name} 下载链接为空")
        }
        val maxRetries = 4
        var attempt = 0
        while (true) {
            val result = target.toPath().downloadFileFrom(
                url = if (attempt < 2) resolvedUrl.rewriteMirrorUrl else resolvedUrl,
                knownSize = artifact.size
            ) { progress ->
                onProgress(progress)
            }
            val error = result.exceptionOrNull()
            if (error != null) {
                target.delete()
                if (attempt >= maxRetries || !isTooManyRequests(error)) {
                    throw error
                }
                val backoffMs = 1000L * (attempt + 1)
                delay(backoffMs)
                attempt++
                continue
            }
            val downloadedSha = target.sha1
            if (!downloadedSha.equals(artifact.sha1, true)) {
                target.delete()
                if (attempt >= maxRetries) {
                    throw IllegalStateException("${target.name}库文件校验失败")
                }
                val backoffMs = 1000L * (attempt + 1)
                delay(backoffMs)
                attempt++
                continue
            }
            return Result.success(target)
        }
    }

    private fun tryExtractLibraryFromInstaller(
        installer: File?,
        artifact: MojangDownloadArtifact,
        target: File
    ): Boolean {
        val installerFile = installer ?: return false
        if (!installerFile.exists()) return false
        val path = artifact.path?.trimStart('/') ?: return false
        return runCatching {
            ZipFile(installerFile).use { zip ->
                val entry = zip.getEntry("maven/$path")
                    ?: zip.getEntry(path)
                    ?: return false
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        }.getOrElse { false }
    }

    private fun resolveArtifactUrl(artifact: MojangDownloadArtifact): String {
        val raw = artifact.url.trim()
        if (raw.isNotEmpty()) return raw
        val path = artifact.path?.trim().orEmpty()
        if (path.isEmpty()) return raw
        val base = when {
            path.startsWith("net/minecraftforge/") -> "https://maven.minecraftforge.net"
            path.startsWith("cpw/mods/") -> "https://maven.minecraftforge.net"
            else -> "https://libraries.minecraft.net"
        }
        return "${base.trimEnd('/')}/${path.trimStart('/')}"
    }

    private fun isTooManyRequests(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("429")
    }

    fun downloadLibrariesTask2(libraries: List<MojangLibrary>): Task2 {
        val filtered = libraries.filter { it.shouldDownloadByArch() }
        if (filtered.isEmpty()) {
            return Task2.Leaf("下载运行库") { ctx ->
                ctx.emit(Task2Progress("无需下载", 1f))
            }
        }
        val subTasks = filtered.map { library ->
            Task2.Leaf("运行库 ${library.name}") { ctx ->
                downloadSingleLibraryTask2(library, ctx)
            }
        }
        return Task2.Group(
            title = "下载${filtered.size}个运行库",
            children = subTasks
        )
    }

    fun downloadAssetsTask2(manifest: MojangVersionManifest): Task2 {
        val assetIndexMeta = manifest.assetIndex ?: return Task2.Leaf("下载资源") { ctx ->
            ctx.emit(Task2Progress("找不到资源", 0f))
        }
        val metaJson = loadResourceStream("mcmeta/assets-index/${assetIndexMeta.id}.json").use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val index = serdesJson.decodeFromString<MojangAssetIndexFile>(metaJson)
        if (!assetIndexesDir.exists()) {
            assetIndexesDir.mkdirs()
        }
        assetIndexesDir.resolve("${assetIndexMeta.id}.json").writeText(metaJson)

        val toDownload = mutableListOf<Map.Entry<String, MojangAssetObject>>()
        val toStub = mutableListOf<Map.Entry<String, MojangAssetObject>>()

        index.objects.entries.forEach { entry ->
            when {
                shouldDownloadAsset(entry.key) -> toDownload += entry
                shouldUseEmptySound(entry.key) -> toStub += entry
                else -> Unit
            }
        }

        toStub.forEach { (_, obj) -> writeEmptySoundStub(obj.hash) }

        val linkPlans = mutableListOf<AssetLinkPlan>()
        val grouped = mutableMapOf<String, MutableList<NumberedAsset>>()
        val normal = mutableListOf<Map.Entry<String, MojangAssetObject>>()

        toDownload.forEach { entry ->
            val path = entry.key
            val match = numberedAssetRegex.matchEntire(path)
            if (match == null) {
                normal += entry
            } else {
                val baseKey = match.groupValues[1] + match.groupValues[3]
                val number = match.groupValues[2].toIntOrNull()
                if (number == null) {
                    normal += entry
                } else {
                    grouped.getOrPut(baseKey) { mutableListOf() }
                        .add(NumberedAsset(path, entry.value, number))
                }
            }
        }

        val compacted = mutableListOf<Map.Entry<String, MojangAssetObject>>()
        compacted += normal
        grouped.values.forEach { group ->
            if (group.size <= 1) {
                val only = group.first()
                compacted += mapEntry(only.path, only.asset)
                return@forEach
            }
            val preferred = group.firstOrNull { it.number == 1 }
                ?: group.minBy { it.number }
            compacted += mapEntry(preferred.path, preferred.asset)
            group.filter { it != preferred }.forEach { other ->
                if (!other.asset.hash.equals(preferred.asset.hash, true)) {
                    linkPlans += AssetLinkPlan(other.asset.hash, preferred.asset.hash, other.path)
                }
            }
        }

        val subTasks = compacted.map { (path, obj) ->
            Task2.Leaf("资源 $path") { ctx ->
                ctx.emit(Task2Progress("开始下载...", 0f))
                downloadAssetObject(path, obj) { prog ->
                    ctx.emit(
                        Task2Progress(
                            "${prog.bytesDownloaded.humanFileSize}/${prog.totalBytes.humanFileSize}",
                            prog.fraction
                        )
                    )
                }.getOrThrow()
                ctx.emit(Task2Progress("下载完成", 1f))
            }
        }

        if (linkPlans.isNotEmpty()) {
            val linksTask = Task2.Leaf("链接相似资源") { ctx ->
                linkPlans.forEachIndexed { index, plan ->
                    createObjectLink(plan.fromHash, plan.toHash)
                    ctx.emit(
                        Task2Progress(
                            "已链接 ${index + 1}/${linkPlans.size}",
                            (index + 1).toFloat() / linkPlans.size
                        )
                    )
                }
            }
            return Task2.Group(
                title = "下载${compacted.size}个音频资源",
                children = subTasks + linksTask
            )
        }

        return Task2.Group(
            title = "下载${compacted.size}个音频资源",
            children = subTasks
        )
    }

    private suspend fun downloadAssetObject(
        path: String,
        asset: MojangAssetObject,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        val hash = asset.hash.lowercase(Locale.ROOT)
        val targetDir = assetObjectsDir.resolve(hash.substring(0, 2))
        val targetFile = targetDir.resolve(hash)

        if (targetFile.exists()) {
            if (targetFile.length() == asset.size) {
                val existingSha = runCatching { targetFile.sha1 }.getOrNull()
                if (existingSha != null && existingSha.equals(hash, true)) {
                    return Result.success(targetFile)
                }
            }
        }

        targetDir.mkdirs()
        val downloadUrl = buildAssetUrl(hash)

        val maxRetries = 4
        var attempt = 0
        while (true) {
            val result = targetFile.toPath().downloadFileFrom(
                url = downloadUrl,
                knownSize = asset.size
            ) { progress ->
                onProgress(progress)
            }
            val error = result.exceptionOrNull() ?: break
            targetFile.delete()
            if (attempt >= maxRetries || !isTooManyRequests(error)) {
                throw error
            }
            val backoffMs = 1000L * (attempt + 1)
            delay(backoffMs)
            attempt++
        }

        if (targetFile.length() != asset.size) {
            targetFile.delete()
            throw IllegalStateException("Size mismatch for $path")
        }
        return Result.success(targetFile)
    }

    private fun readInstallerEntry(installer: File, entryName: String): String {
        ZipFile(installer).use { zip ->
            val entry = zip.getEntry(entryName)
                ?: throw IllegalStateException("安装器中缺少 $entryName")
            zip.getInputStream(entry).bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { reader ->
                return reader.readText()
            }
        }
    }

    private fun extractLibraryDescriptor(raw: String?): String? {
        raw ?: return null
        val trimmed = raw.trim()
        val match = bracketedLibraryRegex.find(trimmed)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun descriptorToLibraryPath(descriptor: String): String {
        val parts = descriptor.split("@", limit = 2)
        val coords = parts[0].split(":")
        require(coords.size >= 3) { "非法的库坐标: $descriptor" }
        val group = coords[0].replace('.', '/')
        val artifact = coords[1]
        val version = coords[2]
        val classifier = coords.getOrNull(3)?.takeIf { it.isNotBlank() }
        val extension = parts.getOrNull(1)?.ifBlank { null } ?: "jar"
        val fileName = buildString {
            append(artifact).append('-').append(version)
            if (classifier != null) append('-').append(classifier)
            append('.').append(extension)
        }
        return "$group/$artifact/$version/$fileName"
    }

    private fun libraryKey(library: MojangLibrary): String {
        val artifactPath = library.downloads.artifact.path.orEmpty()
        val classifierKey = library.downloads.classifiers?.keys?.sorted()?.joinToString(";").orEmpty()
        return "${library.name}|$artifactPath|$classifierKey"
    }

    @Serializable
    data class LoaderInstallProfile(
        val serverJarPath: String? = null,
        val libraries: List<MojangLibrary> = emptyList(),
        val data: Map<String, LoaderInstallData> = emptyMap(),
    )

    @Serializable
    data class LoaderInstallData(
        val client: String? = null,
        val server: String? = null,
    )

    private fun shouldDownloadAsset(path: String): Boolean {
        if (path == "minecraft/resourcepacks/programmer_art.zip") return false
        if (path.startsWith("realms/")) {
            return path == "realms/lang/en_us.json"
        }
        if (path.startsWith("minecraft/lang/")) {
            return path.equals("minecraft/lang/zh_cn.json", ignoreCase = true)
        }
        if (path.startsWith("minecraft/sounds/")) {
            val rest = path.removePrefix("minecraft/sounds/")
            if (rest.startsWith("records/") || rest.startsWith("music/") || rest.startsWith("ambient/")) {
                return false
            }
        }
        return true
    }

    private fun shouldUseEmptySound(path: String): Boolean {
        if (!path.endsWith(".ogg", ignoreCase = true)) return false
        if (!path.startsWith("minecraft/sounds/")) return false
        val rest = path.removePrefix("minecraft/sounds/")
        return rest.startsWith("records/") || rest.startsWith("music/") || rest.startsWith("ambient/")
    }

    private fun writeEmptySoundStub(hash: String) {
        val targetDir = assetObjectsDir.resolve(hash.substring(0, 2))
        val targetFile = targetDir.resolve(hash)
        if (targetFile.exists()) return
        targetDir.mkdirs()
        loadResourceStream("assets/empty.ogg").use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun buildAssetUrl(hash: String): String {
        val base = "https://resources.download.minecraft.net".rewriteMirrorUrl
        val sub = hash.take(2)
        return "$base/$sub/$hash"
    }

    private data class NumberedAsset(
        val path: String,
        val asset: MojangAssetObject,
        val number: Int
    )

    private data class AssetLinkPlan(
        val fromHash: String,
        val toHash: String,
        val path: String
    )

    private fun mapEntry(path: String, asset: MojangAssetObject): Map.Entry<String, MojangAssetObject> {
        return object : Map.Entry<String, MojangAssetObject> {
            override val key: String = path
            override val value: MojangAssetObject = asset
        }
    }

    private fun createObjectLink(fromHash: String, toHash: String) {
        if (fromHash.equals(toHash, true)) return
        val fromDir = assetObjectsDir.resolve(fromHash.substring(0, 2))
        val toDir = assetObjectsDir.resolve(toHash.substring(0, 2))
        val fromFile = fromDir.resolve(fromHash)
        val toFile = toDir.resolve(toHash)
        if (fromFile.exists() || !toFile.exists()) return
        fromDir.mkdirs()
        runCatching {
            Files.createSymbolicLink(fromFile.toPath(), toFile.toPath())
        }.onFailure {
            runCatching {
                Files.copy(toFile.toPath(), fromFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    fun downloadLoaderTask2(version: McVersion, loader: ModLoader): Task2 {
        val holder = LoaderInstallHolder(version = version, loader = loader)
        return Task2.Sequence(
            title = "安装 $loader",
            children = listOf(
                Task2.Leaf("下载$loader 安装器") { ctx ->
                    prepareInstallerTask2(holder, ctx)
                },
                Task2.Leaf("解析安装器") { ctx ->
                    parseInstallerTask2(holder, ctx)
                },
                Task2.Leaf("下载${loader}服务端") { ctx ->
                    downloadServerTask2(holder, ctx)
                },
                Task2.Leaf("下载$loader 依赖") { ctx ->
                    downloadLibrariesTask2(holder.loaderLibraries, ctx, holder.installer)
                },
                Task2.Leaf("下载Mojmap") { ctx ->
                    downloadMojmapIfNeededTask2(holder, ctx)
                },
                Task2.Leaf("运行安装器") { ctx ->
                    runInstallerBootstrapperTask2(holder, ctx)
                },
                Task2.Leaf("运行安装器服务端") { ctx ->
                    runServerInstallerBootstrapperTask2(holder, ctx)
                }
            ),
        )
    }


    data class LoaderInstallHolder(
        val version: McVersion,
        val loader: ModLoader,
        var installer: File? = null,
        var installBooter: File? = null,
        var loaderVersionManifest: MojangVersionManifest = version.metadata,
        var installProfile: LoaderInstallProfile? = null,
        var loaderLibraries: List<MojangLibrary> = emptyList()
    )

    private suspend fun prepareInstallerTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        val loaderMeta = holder.version.loaderVersions[holder.loader]
            ?: error("未配置 ${holder.loader} 安装器下载链接")
        val mcDir = ClientDirs.mcDir
        "launcher_profiles.json".let { exportResource(it, File(mcDir, it)) }
        val installBooter =
            "forge-install-bootstrapper.jar".let { File(mcDir, it).also { f -> exportResource(it, f) } }
        val installer = mcDir.resolve("${holder.version.mcVer}-${holder.loader}-installer.jar")
        installer.parentFile?.mkdirs()
        holder.installBooter = installBooter
        holder.installer = installer

        if (installer.exists() && installer.sha1 == loaderMeta.installerSha1) {
            ctx.emit(Task2Progress("安装器已存在", 1f))
            return
        }

        ctx.emit(Task2Progress("开始下载...", 0f))
        installer.toPath().downloadFileFrom(loaderMeta.installerUrl.rewriteMirrorUrl) { progress ->
            ctx.emit(
                Task2Progress(
                    "${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                    progress.fraction
                )
            )
        }.getOrThrow()
        ctx.emit(Task2Progress("下载完成", 1f))
    }

    private fun parseInstallerTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        val installer = holder.installer ?: error("安装器未准备")
        val versionJsonText = readInstallerEntry(installer, "version.json")
        val loaderVersionManifest = serdesJson.decodeFromString<MojangVersionManifest>(versionJsonText)
        val loaderVersionDir = versionListDir.resolve(loaderVersionManifest.id).apply { mkdirs() }
        File(loaderVersionDir, "${loaderVersionManifest.id}.json").writeText(loaderVersionManifest.json)

        val installProfileText = readInstallerEntry(installer, "install_profile.json")
        val installProfile = serdesJson.decodeFromString<LoaderInstallProfile>(installProfileText)
        val loaderLibraries = (installProfile.libraries + loaderVersionManifest.libraries)
            .distinctBy { libraryKey(it) }

        holder.loaderVersionManifest = loaderVersionManifest
        holder.installProfile = installProfile
        holder.loaderLibraries = loaderLibraries

        ctx.emit(Task2Progress("解析完成", 1f))
    }

    private suspend fun downloadLibrariesTask2(
        libraries: List<MojangLibrary>,
        ctx: Task2Context,
        installer: File? = null
    ) {
        val filtered = libraries.filter { it.shouldDownloadByArch() }
        if (filtered.isEmpty()) {
            ctx.emit(Task2Progress("无需下载", 1f))
            return
        }
        Task2.Group(
            title = "下载${filtered.size}个运行库",
            children = filtered.map { library ->
                Task2.Leaf("运行库 ${library.name}") { childCtx ->
                    downloadSingleLibraryTask2(library, childCtx, installer)
                }
            }
        ).runInline(ctx)
    }

    private suspend fun downloadSingleLibraryTask2(
        library: MojangLibrary,
        ctx: Task2Context,
        installer: File? = null
    ) {
        ctx.emit(Task2Progress("开始下载...", 0f))
        downloadLibraryArtifact(library, installer = installer) { progress ->
            ctx.emit(
                Task2Progress(
                    "${library.name} ${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                    progress.fraction
                )
            )
        }.getOrThrow()
        library.nativeArtifact()?.let { nativeArtifact ->
            val nativePath = nativeArtifact.path ?: return@let
            val nativeFile = File(libsDir, nativePath)
            downloadLibraryArtifact(nativeArtifact, nativeFile, installer = installer) { progress ->
                ctx.emit(
                    Task2Progress(
                        "${library.name} ${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                        progress.fraction
                    )
                )
            }.getOrThrow()
        }
        ctx.emit(Task2Progress("下载完成", 1f))
    }

    private suspend fun downloadMojmapIfNeededTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        val installProfile = holder.installProfile ?: return
        val vanillaManifest = holder.version.metadata
        val mojmaps = installProfile.data["MOJMAPS"] ?: return
        val downloads = vanillaManifest.downloads ?: return
        val tasks = mutableListOf<Triple<String, MojangDownloadArtifact, String>>()
        extractLibraryDescriptor(mojmaps.client)?.let { descriptor ->
            downloads.clientMappings?.let { artifact ->
                tasks += Triple("客户端", artifact, descriptor)
            }
        }
        extractLibraryDescriptor(mojmaps.server)?.let { descriptor ->
            downloads.serverMappings?.let { artifact ->
                tasks += Triple("服务端", artifact, descriptor)
            }
        }
        if (tasks.isEmpty()) {
            ctx.emit(Task2Progress("无需下载", 1f))
            return
        }
        val total = tasks.size
        tasks.forEachIndexed { index, (label, artifact, descriptor) ->
            val relativePath = descriptorToLibraryPath(descriptor)
            val target = File(libsDir, relativePath)
            downloadArtifact(label, artifact, target) { progress ->
                ctx.emit(
                    Task2Progress(
                        "$label ${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                        progress.fraction
                    )
                )
            }.getOrThrow()
            ctx.emit(Task2Progress("已完成 ${index + 1}/$total", (index + 1).toFloat() / total))
        }
    }

    internal fun runInstallerBootstrapperTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        if (!calebxzhou.rdi.client.ui.isDesktop) {
            ctx.emit(Task2Progress("Android跳过 (由FCL处理)", 1f))
            return
        }
        runInstallerBootstrapperDesktop(holder, ctx.asLegacyTaskContext())
    }

    internal fun runServerInstallerBootstrapperTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        if (!calebxzhou.rdi.client.ui.isDesktop) {
            ctx.emit(Task2Progress("Android跳过 (由FCL处理)", 1f))
            return
        }
        runServerInstallerBootstrapperDesktop(holder, ctx.asLegacyTaskContext())
    }

    private fun Task2Context.asLegacyTaskContext(): TaskContext = TaskContext(
        emitProgress = { progress -> emit(Task2Progress(progress.message, progress.fraction)) },
        isCancelled = isCancelled
    )

    private suspend fun downloadArtifact(
        label: String,
        artifact: MojangDownloadArtifact,
        target: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        if (target.exists()) {
            val existingSha = runCatching { target.sha1 }.getOrNull()
            if (existingSha != null && existingSha.equals(artifact.sha1, true)) {
                return Result.success(target)
            }
        }
        target.parentFile?.mkdirs()
        val resolvedUrl = resolveArtifactUrl(artifact)
        if (resolvedUrl.isBlank()) {

            throw IllegalStateException("$label 下载链接为空")
        }
        target.toPath().downloadFileFrom(
            url = resolvedUrl.rewriteMirrorUrl,
            knownSize = artifact.size
        ) { progress ->
            onProgress(progress)
        }.getOrElse {
            target.delete()
            throw it
        }
        val downloadedSha = target.sha1
        if (!downloadedSha.equals(artifact.sha1, true)) {
            target.delete()
            throw IllegalStateException("$label 校验失败")
        }
        return Result.success(target)
    }

    // ---- Argument resolution (used by desktop game launching) ----

    internal fun resolveArgumentList(source: List<JsonElement>): List<String> {
        val args = mutableListOf<String>()
        val ruleListSerializer = ListSerializer(MojangRule.serializer())
        source.forEach { element ->
            when (element) {
                is JsonPrimitive -> if (element.isString) args += element.content
                is JsonObject -> {
                    val rules = element["rules"]?.let { serdesJson.decodeFromJsonElement(ruleListSerializer, it) }
                    if (!rulesAllow(rules)) return@forEach
                    val valueElement = element["value"] ?: return@forEach
                    when (valueElement) {
                        is JsonPrimitive -> if (valueElement.isString) args += valueElement.content
                        is JsonArray -> valueElement.forEach { item ->
                            if (item is JsonPrimitive && item.isString) args += item.content
                        }

                        else -> {}
                    }
                }

                else -> {}
            }
        }
        return args
    }

    internal fun rulesAllow(rules: List<MojangRule>?): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        rules.forEach { rule ->
            if (rule.matchesHost()) {
                allowed = rule.action == MojangRuleAction.allow
            }
        }
        return allowed
    }

    private fun MojangRule.matchesHost(): Boolean {
        os?.let { spec ->
            val osName = spec.name
            if (osName != null && !hostOs.ruleOsName.equals(osName, true)) return false
            val archSpec = spec.arch?.lowercase(Locale.ROOT)
            if (archSpec != null && !hostOsArchRaw.contains(archSpec)) return false
            val versionSpec = spec.version
            if (versionSpec != null) {
                val regex = runCatching { Regex(versionSpec) }.getOrNull()
                val matches = regex?.containsMatchIn(hostOsVersionRaw) ?: hostOsVersionRaw.contains(versionSpec, true)
                if (!matches) return false
            }
        }
        val requiredFeatures = features ?: return true
        if (requiredFeatures.isEmpty()) return true
        return requiredFeatures.all { (feature, expected) ->
            launcherFeatures[feature] == expected
        }
    }

    internal fun MojangVersionManifest.buildClasspath(): List<String> {
        val entries = this.libraries
            .asSequence()
            .filter { lib -> lib.shouldDownloadByArch() }
            .mapNotNull { lib -> lib.downloads.artifact.path }
            .map { File(libsDir, it).absolutePath }
            .toMutableList()
            .distinct()
            .toList()
        return entries
    }
}

/**
 * Desktop-only installer bootstrapper. Implemented via extension to keep
 * ProcessBuilder/javaExePath references out of commonMain compilation unit.
 * Called only when isDesktop is true.
 */
internal expect fun GameService.runInstallerBootstrapperDesktop(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
)

internal expect fun GameService.runServerInstallerBootstrapperDesktop(
    holder: GameService.LoaderInstallHolder,
    ctx: TaskContext
)
