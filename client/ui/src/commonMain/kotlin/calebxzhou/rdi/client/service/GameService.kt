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
import java.io.IOException
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
    private val hostNativeArch = if (
        hostOsArchRaw.contains("64") ||
        hostOsArchRaw.contains("amd64") ||
        hostOsArchRaw.contains("x86_64") ||
        hostOsArchRaw.contains("aarch64")
    ) {
        "64"
    } else {
        "32"
    }
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
    private const val VERIFIED_DOWNLOAD_MAX_ATTEMPTS = 6
    private data class DownloadSourcePlan(
        val primaryUrls: List<String>,
        val fallbackUrls: List<String> = emptyList()
    )

    private val String.mirrorCandidateUrl: String
        get() {
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

    private fun buildDownloadSourcePlan(
        officialUrl: String,
        mirrorUrl: String = officialUrl.mirrorCandidateUrl
    ): DownloadSourcePlan {
        val officialUrls = listOf(officialUrl.trim())
            .filter { it.isNotBlank() }
            .distinct()
        val mirrorUrls = listOf(mirrorUrl.trim())
            .filter { it.isNotBlank() }
            .filterNot { it in officialUrls }
            .distinct()
        return if (CONF.preferMcMirror) {
            DownloadSourcePlan(
                primaryUrls = mirrorUrls.ifEmpty { officialUrls },
                fallbackUrls = if (mirrorUrls.isNotEmpty()) officialUrls else emptyList()
            )
        } else {
            DownloadSourcePlan(
                primaryUrls = officialUrls,
                fallbackUrls = mirrorUrls
            )
        }
    }

    private fun buildClientDownloadSourcePlan(manifest: MojangVersionManifest): DownloadSourcePlan {
        val officialUrl = manifest.downloads?.client?.url?.trim().orEmpty()
        val mirrorUrl = "https://bmclapi2.bangbang93.com/version/${manifest.id}/client"
        return buildDownloadSourcePlan(officialUrl, mirrorUrl)
    }

    private fun buildServerDownloadSourcePlan(mcVerStr: String, artifact: MojangDownloadArtifact): DownloadSourcePlan {
        val officialUrl = artifact.url.trim()
        val mirrorUrl = "https://bmclapi2.bangbang93.com/version/${mcVerStr}/server"
        return buildDownloadSourcePlan(officialUrl, mirrorUrl)
    }

    private fun buildAssetDownloadSourcePlan(hash: String): DownloadSourcePlan {
        val officialBase = "https://resources.download.minecraft.net"
        val sub = hash.take(2)
        val officialUrl = "${officialBase.trimEnd('/')}/$sub/$hash"
        return buildDownloadSourcePlan(officialUrl)
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
            title = "下载 ${version.mcVer}",
            children = tasks
        )
    }

    fun downloadAssetsOnlyTask2(version: McVersion): Task2 {
        return Task2.Sequence(
            title = "下载 ${version.mcVer} assets",
            children = listOf(downloadAssetsTask2(version.metadata))
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
            val clientArtf = manifest.downloads?.client ?: run {
                ctx.emit(Task2Progress("缺少客户端下载信息", 0f))
                return@Leaf
            }
            val versionDir = versionListDir.resolve(manifest.id).apply { mkdirs() }
            File(versionDir, "${manifest.id}.json").writeText(manifest.json)
            val target = File(versionDir, "${manifest.id}.jar")
            ctx.emit(Task2Progress("开始下载...", 0.1f))
            downloadArtifact(
                label = "客户端核心 ${manifest.id}",
                artifact = clientArtf,
                target = target,
                sourcePlan = buildClientDownloadSourcePlan(manifest)
            ) { progress ->
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
        val server = holder.version.metadata.downloads?.server ?: run {
            ctx.emit(Task2Progress("缺少服务端下载信息", 0f))
            return
        }
        val serverTargetFile = holder.installProfile?.serverJarPath
            ?.replace("{LIBRARY_DIR}", libsDir.absolutePath)
            ?.replace("{MINECRAFT_VERSION}", mcVerStr)
            ?.let { File(it).apply { parentFile?.mkdirs() } }
            ?: ClientDirs.mcDir.resolve("minecraft_server.${mcVerStr}.jar")
        ctx.emit(Task2Progress("开始下载...", 0.1f))
        downloadArtifact(
            label = "服务端核心 $mcVerStr",
            artifact = server,
            target = serverTargetFile,
            sourcePlan = buildServerDownloadSourcePlan(mcVerStr, server)
        ) { progress ->
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
        return natives?.get(hostOs.ruleOsName)?.replace("\${arch}", hostNativeArch)
    }

    private fun MojangLibrary.nativeArtifact(): MojangDownloadArtifact? {
        val key = nativeClassifierKey() ?: return null
        return downloads.classifiers?.get(key)
    }

    internal fun MojangLibrary.mainArtifact(): MojangDownloadArtifact? {
        downloads.artifact?.let { return it }
        if (!downloads.classifiers.isNullOrEmpty() || !natives.isNullOrEmpty()) {
            return null
        }
        val descriptor = name.takeIf { it.isNotBlank() } ?: return null
        val path = descriptorToLibraryPath(descriptor)
        val baseUrl = url?.trim().orEmpty().ifBlank {
            when {
                path.startsWith("net/minecraftforge/") -> "https://maven.minecraftforge.net"
                path.startsWith("cpw/mods/") -> "https://maven.minecraftforge.net"
                else -> "https://libraries.minecraft.net"
            }
        }
        return MojangDownloadArtifact(
            sha1 = checksums.firstOrNull().orEmpty(),
            size = 0L,
            url = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}",
            path = path
        )
    }

    private val List<MojangLibrary>.filterNativeOnly
        get() = this.filter { it.nativeArtifact() != null }
            .filter { it.shouldDownloadByArch() }
    val MojangLibrary.file
        get() = mainArtifact()?.path?.let { File(libsDir, it) }
            ?: error("库${name}缺少artifact路径")


    internal suspend fun downloadLibraryArtifact(
        library: MojangLibrary,
        installer: File? = null,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        val artifact = library.mainArtifact()
            ?: return Result.failure(IllegalStateException("运行库${library.name}缺少artifact"))
        val relativePath = artifact.path?.takeIf { it.isNotBlank() }
            ?: runCatching { descriptorToLibraryPath(library.name) }
                .getOrElse {
                    return Result.failure(IllegalStateException("运行库${library.name}缺少库路径"))
                }
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
            if (artifact.sha1.isBlank() || existingSha != null && existingSha.equals(artifact.sha1, true)) {
                return Result.success(target)
            }
        }
        target.parentFile?.mkdirs()

        val rawUrl = artifact.url.trim()
        if (rawUrl.isEmpty()) {
            val extracted = tryExtractLibraryFromInstaller(installer, artifact, target)
            if (extracted) {
                val extractedSha = runCatching { target.sha1 }.getOrNull()
                if (artifact.sha1.isBlank() || extractedSha != null && extractedSha.equals(artifact.sha1, true)) {
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
        val sourcePlan = buildDownloadSourcePlan(resolvedUrl)
        return downloadVerifiedArtifact(
            label = "${target.name}库文件",
            artifact = artifact,
            target = target,
            sourcePlan = sourcePlan,
            onProgress = onProgress
        )
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

    private fun DownloadSourcePlan.preferFallback(): DownloadSourcePlan {
        if (fallbackUrls.isEmpty()) return this
        return DownloadSourcePlan(
            primaryUrls = fallbackUrls,
            fallbackUrls = primaryUrls
        )
    }

    private suspend fun downloadVerifiedArtifact(
        label: String,
        artifact: MojangDownloadArtifact,
        target: File,
        sourcePlan: DownloadSourcePlan,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        var attempt = 1
        var currentSourcePlan = sourcePlan
        while (true) {
            val result = target.toPath().downloadFileFrom(
                primaryUrls = currentSourcePlan.primaryUrls,
                fallbackUrls = currentSourcePlan.fallbackUrls,
                knownSize = artifact.size
            ) { progress ->
                onProgress(progress)
            }
            val error = result.exceptionOrNull()
            if (error != null) {
                target.delete()
                if (attempt >= VERIFIED_DOWNLOAD_MAX_ATTEMPTS || !isRetryableArtifactDownloadFailure(error)) {
                    throw error
                }
                scheduleArtifactRetry(label, attempt, error)
                attempt++
                continue
            }

            val downloadedSha = try {
                target.sha1
            } catch (error: Throwable) {
                target.delete()
                if (attempt >= VERIFIED_DOWNLOAD_MAX_ATTEMPTS) {
                    throw error
                }
                scheduleArtifactRetry("$label 计算校验值", attempt, error)
                attempt++
                continue
            }
            if (artifact.sha1.isBlank() || downloadedSha.equals(artifact.sha1, true)) {
                return Result.success(target)
            }

            target.delete()
            val mismatchError = IllegalStateException("$label 校验失败")
            if (attempt >= VERIFIED_DOWNLOAD_MAX_ATTEMPTS) {
                throw mismatchError
            }
            currentSourcePlan = currentSourcePlan.preferFallback()
            scheduleArtifactRetry(label, attempt, mismatchError, preferFallback = true)
            attempt++
        }
    }

    private suspend fun scheduleArtifactRetry(
        label: String,
        attempt: Int,
        error: Throwable,
        preferFallback: Boolean = false
    ) {
        val backoffMs = 1000L * attempt
        val suffix = if (preferFallback) "，下次优先备用源" else ""
        lgr.warn { "$label 第$attempt 次失败，${backoffMs}ms后重试$suffix: ${error.message}" }
        delay(backoffMs)
    }

    private fun isTooManyRequests(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("429")
    }

    private fun isRetryableArtifactDownloadFailure(error: Throwable): Boolean =
        isTooManyRequests(error) || error is IOException

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
        val sourcePlan = buildAssetDownloadSourcePlan(hash)

        val maxRetries = 4
        var attempt = 0
        while (true) {
            val result = targetFile.toPath().downloadFileFrom(
                primaryUrls = sourcePlan.primaryUrls,
                fallbackUrls = sourcePlan.fallbackUrls,
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

    private fun readInstallerEntryOrNull(installer: File, entryName: String): String? =
        runCatching { readInstallerEntry(installer, entryName) }.getOrNull()

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
        val artifactPath = library.mainArtifact()?.path.orEmpty()
        val classifierKey = library.downloads.classifiers?.keys?.sorted()?.joinToString(";").orEmpty()
        return "${library.name}|$artifactPath|$classifierKey"
    }

    private val classpathOverrideArtifacts = setOf(
        "com.google.code.gson:gson",
        "com.google.guava:guava",
        "commons-codec:commons-codec",
        "commons-io:commons-io",
        "commons-logging:commons-logging",
        "it.unimi.dsi:fastutil",
        "net.java.dev.jna:jna",
        "net.java.jinput:jinput",
        "net.sf.jopt-simple:jopt-simple",
        "org.apache.commons:commons-compress",
        "org.apache.commons:commons-lang3",
        "org.apache.httpcomponents:httpclient",
        "org.apache.httpcomponents:httpcore",
        "org.apache.logging.log4j:log4j-api",
        "org.apache.logging.log4j:log4j-core",
        "org.apache.logging.log4j:log4j-slf4j18-impl",
        "org.apache.logging.log4j:log4j-slf4j2-impl",
        "org.slf4j:slf4j-api",
    )

    // Cleanroom ships full replacements for a few legacy 1.12 libraries.
    private val cleanroomRemovedBaseArtifacts = setOf(
        "org.lwjgl.lwjgl:lwjgl",
        "org.lwjgl.lwjgl:lwjgl_util",
        "org.lwjgl.lwjgl:lwjgl-platform",
        "com.ibm.icu:icu4j-core-mojang",
        "net.java.dev.jna:platform",
        "oshi-project:oshi-core",
    )

    private fun classpathOverrideKey(library: MojangLibrary): String? {
        val coords = library.name.split(':')
        if (coords.size < 2) return null
        return "${coords[0]}:${coords[1]}".takeIf { it in classpathOverrideArtifacts }
    }

    private fun libraryGroupArtifact(library: MojangLibrary): String? {
        val coords = library.name.split(':')
        if (coords.size < 2) return null
        return "${coords[0]}:${coords[1]}"
    }

    private fun addClasspathCompatibilityLibraries(entries: List<String>): List<String> {
        val files = entries.map(::File).toMutableList()
        val hasSlf4jBinding = files.any { it.name.startsWith("log4j-slf4j18-impl-") }
        val hasSlf4jApi = files.any { it.name.startsWith("slf4j-api-") }
        if (hasSlf4jBinding && !hasSlf4jApi) {
            val slf4jApiCandidates = listOf(
                libsDir.resolve("org/slf4j/slf4j-api/1.8.0-beta4/slf4j-api-1.8.0-beta4.jar"),
                libsDir.resolve("org/slf4j/slf4j-api/2.0.1/slf4j-api-2.0.1.jar"),
                libsDir.resolve("org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar")
            )
            slf4jApiCandidates.firstOrNull(File::exists)?.let { files += it }
        }
        return files.map{it.absolutePath}.distinct()
    }

    @Serializable
    data class LoaderInstallProfile(
        val serverJarPath: String? = null,
        val libraries: List<MojangLibrary> = emptyList(),
        val data: Map<String, LoaderInstallData> = emptyMap(),
        val versionInfo: MojangVersionManifest? = null,
        val install: LoaderLegacyInstall? = null,
        val json: String? = null,
    )

    @Serializable
    data class LoaderInstallData(
        val client: String? = null,
        val server: String? = null,
    )

    @Serializable
    data class LoaderLegacyInstall(
        val profileName: String? = null,
        val target: String? = null,
        val path: String? = null,
        val version: String? = null,
        val filePath: String? = null,
        val minecraft: String? = null,
        val mirrorList: String? = null,
        val logo: String? = null,
    )

    private fun shouldDownloadAsset(path: String): Boolean {
        if (path == "minecraft/resourcepacks/programmer_art.zip") return false
        if (path.startsWith("realms/")) {
            return path == "realms/lang/en_us.json"
        }
        if (path.startsWith("minecraft/lang/")) {
            return path.equals("minecraft/lang/zh_cn.json", ignoreCase = true) ||
             path.equals("minecraft/lang/zh_cn.lang", ignoreCase = true)
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
        Files.copy(toFile.toPath(), fromFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
        var loaderLibraries: List<MojangLibrary> = emptyList(),
        var clientInstallerAlreadyHandled: Boolean = false,
        var serverInstallerAlreadyHandled: Boolean = false,
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
        val sourcePlan = buildDownloadSourcePlan(loaderMeta.installerUrl)
        installer.toPath().downloadFileFrom(
            primaryUrls = sourcePlan.primaryUrls,
            fallbackUrls = sourcePlan.fallbackUrls
        ) { progress ->
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
        val installProfileText = readInstallerEntry(installer, "install_profile.json")
        val installProfile = serdesJson.decodeFromString<LoaderInstallProfile>(installProfileText)
        val loaderVersionManifest = resolveLoaderVersionManifest(holder, installer, installProfile)
        val loaderVersionDir = versionListDir.resolve(loaderVersionManifest.id).apply { mkdirs() }
        File(loaderVersionDir, "${loaderVersionManifest.id}.json").writeText(loaderVersionManifest.json)
        val loaderLibraries = (installProfile.libraries + loaderVersionManifest.libraries)
            .distinctBy { libraryKey(it) }

        holder.loaderVersionManifest = loaderVersionManifest
        holder.installProfile = installProfile
        holder.loaderLibraries = loaderLibraries

        ctx.emit(Task2Progress("解析完成", 1f))
    }

    private fun resolveLoaderVersionManifest(
        holder: LoaderInstallHolder,
        installer: File,
        installProfile: LoaderInstallProfile
    ): MojangVersionManifest {
        val modernManifest = readInstallerEntryOrNull(installer, "version.json")
            ?.let { serdesJson.decodeFromString<MojangVersionManifest>(it) }
        if (modernManifest != null) {
            holder.clientInstallerAlreadyHandled = false
            holder.serverInstallerAlreadyHandled = false
            return modernManifest.normalizeLoaderManifest(holder, installProfile)
        }

        installProfile.versionInfo?.let { legacyManifest ->
            extractLegacyForgeUniversalJarIfNeeded(installer, installProfile)
            holder.clientInstallerAlreadyHandled = true
            holder.serverInstallerAlreadyHandled = true
            return legacyManifest.normalizeLoaderManifest(holder, installProfile)
        }

        val legacyJsonPath = installProfile.json
            ?.trim()
            ?.trimStart('/')
            ?.takeIf { it.isNotEmpty() }
        if (legacyJsonPath != null) {
            val legacyManifest = serdesJson.decodeFromString<MojangVersionManifest>(
                readInstallerEntry(installer, legacyJsonPath)
            )
            extractInstallerMavenLibraries(installer)
            holder.clientInstallerAlreadyHandled = true
            holder.serverInstallerAlreadyHandled = true
            return legacyManifest.normalizeLoaderManifest(holder, installProfile)
        }

        throw IllegalStateException("安装器中既没有version.json，也没有可用的legacy versionInfo/json")
    }

    private fun MojangVersionManifest.normalizeLoaderManifest(
        holder: LoaderInstallHolder,
        installProfile: LoaderInstallProfile
    ): MojangVersionManifest {
        val expectedId = installProfile.install?.target
            ?.takeIf { it.isNotBlank() }
            ?: holder.version.loaderVersions[holder.loader]?.dirName
            ?: id
        return copy(
            id = expectedId,
            inheritsFrom = inheritsFrom?.takeIf { it.isNotBlank() } ?: holder.version.mcVer,
            jar = jar?.takeIf { it.isNotBlank() } ?: holder.version.mcVer
        )
    }

    private fun extractLegacyForgeUniversalJarIfNeeded(installer: File, installProfile: LoaderInstallProfile) {
        val install = installProfile.install ?: return
        val libraryDescriptor = install.path?.takeIf { it.isNotBlank() } ?: return
        val entryName = install.filePath?.trimStart('/')?.takeIf { it.isNotBlank() } ?: return
        val target = File(libsDir, descriptorToLibraryPath(libraryDescriptor))
        if (target.exists() && target.length() > 0L) return
        runCatching {
            ZipFile(installer).use { zip ->
                val entry = zip.getEntry(entryName)
                    ?: throw IllegalStateException("安装器中缺少旧版Forge主Jar: $entryName")
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }.onFailure { error ->
            lgr.warn(error) { "提取旧版Forge主Jar失败，将回退为网络下载: $entryName" }
        }
    }

    private fun extractInstallerMavenLibraries(installer: File) {
        runCatching {
            ZipFile(installer).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.startsWith("maven/")) continue
                    val relativePath = entry.name.removePrefix("maven/")
                    if (relativePath.isBlank()) continue
                    val target = File(libsDir, relativePath)
                    if (target.exists() && target.length() == entry.size) continue
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }.onFailure { error ->
            lgr.warn(error) { "提取安装器内置maven运行库失败，将回退为网络下载" }
        }
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
        library.mainArtifact()?.let {
            downloadLibraryArtifact(library, installer = installer) { progress ->
                ctx.emit(
                    Task2Progress(
                        "${library.name} ${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                        progress.fraction
                    )
                )
            }.getOrThrow()
        }
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
        if (holder.clientInstallerAlreadyHandled) {
            ctx.emit(Task2Progress("旧版Forge安装已完成", 1f))
            return
        }
        runInstallerBootstrapperDesktop(holder, ctx.asLegacyTaskContext())
    }

    internal fun runServerInstallerBootstrapperTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        if (!calebxzhou.rdi.client.ui.isDesktop) {
            ctx.emit(Task2Progress("Android跳过 (由FCL处理)", 1f))
            return
        }
        if (holder.serverInstallerAlreadyHandled) {
            ctx.emit(Task2Progress("旧版Forge服务端安装已完成", 1f))
            return
        }
        runServerInstallerBootstrapperDesktop(holder, ctx.asLegacyTaskContext())
    }

    private fun Task2Context.asLegacyTaskContext(): TaskContext = TaskContext(
        emitProgress = { progress -> emit(Task2Progress(progress.message, progress.fraction)) },
        isCancelled = isCancelled
    )

    internal suspend fun downloadArtifact(
        label: String,
        artifact: MojangDownloadArtifact,
        target: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = downloadArtifact(
        label = label,
        artifact = artifact,
        target = target,
        sourcePlan = buildDownloadSourcePlan(resolveArtifactUrl(artifact)),
        onProgress = onProgress
    )

    private suspend fun downloadArtifact(
        label: String,
        artifact: MojangDownloadArtifact,
        target: File,
        sourcePlan: DownloadSourcePlan,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        if (target.exists()) {
            val existingSha = runCatching { target.sha1 }.getOrNull()
            if (existingSha != null && existingSha.equals(artifact.sha1, true)) {
                return Result.success(target)
            }
        }
        target.parentFile?.mkdirs()
        if (sourcePlan.primaryUrls.isEmpty() && sourcePlan.fallbackUrls.isEmpty()) {

            throw IllegalStateException("$label 下载链接为空")
        }
        return downloadVerifiedArtifact(
            label = label,
            artifact = artifact,
            target = target,
            sourcePlan = sourcePlan,
            onProgress = onProgress
        )
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

    internal fun MojangVersionManifest.resolveGameArgumentList(): List<String> {
        val modernArgs = resolveArgumentList(arguments.game)
        if (modernArgs.isNotEmpty()) return modernArgs
        return minecraftArguments
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    internal fun MojangVersionManifest.resolveJvmArgumentList(): List<String> {
        return resolveArgumentList(arguments.jvm)
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
            .mapNotNull { lib ->
                lib.mainArtifact()?.path?.takeIf { it.isNotBlank() }
                    ?: runCatching { descriptorToLibraryPath(lib.name) }.getOrNull()
            }
            .map { File(libsDir, it).absolutePath }
            .filter { File(it).exists() }
            .toMutableList()
            .distinct()
            .toList()
        return addClasspathCompatibilityLibraries(entries)
    }

    internal fun buildClasspath(
        baseLibraries: List<MojangLibrary>,
        overrideLibraries: List<MojangLibrary>
    ): List<String> {
        val archMatchedOverrideLibraries = overrideLibraries.filter { it.shouldDownloadByArch() }
        val overrideGroupArtifacts = archMatchedOverrideLibraries.mapNotNull(::libraryGroupArtifact).toSet()
        val removedBaseArtifacts = buildSet {
            if ("com.cleanroommc:lwjglxx" in overrideGroupArtifacts) {
                addAll(cleanroomRemovedBaseArtifacts)
            }
        }
        val filteredOverrideLibraries = archMatchedOverrideLibraries
            .filterNot { libraryGroupArtifact(it) in removedBaseArtifacts }
        val filteredBaseLibraries = baseLibraries
            .filter { it.shouldDownloadByArch() }
            .filterNot { libraryGroupArtifact(it) in removedBaseArtifacts }
        val overrideByKey = filteredOverrideLibraries
            .mapNotNull { library -> classpathOverrideKey(library)?.let { it to library } }
            .toMap()
        val usedOverrideKeys = mutableSetOf<String>()
        val mergedLibraries = filteredBaseLibraries.map { library ->
            val key = classpathOverrideKey(library) ?: return@map library
            overrideByKey[key]?.also { usedOverrideKeys += key } ?: library
        } + filteredOverrideLibraries.filter { library ->
            val key = classpathOverrideKey(library)
            key == null || key !in usedOverrideKeys
        }
        val entries = mergedLibraries
            .asSequence()
            .mapNotNull { lib ->
                lib.mainArtifact()?.path?.takeIf { it.isNotBlank() }
                    ?: runCatching { descriptorToLibraryPath(lib.name) }.getOrNull()
            }
            .map { File(libsDir, it).absolutePath }
            .filter { File(it).exists() }
            .distinct()
            .toList()
        return addClasspathCompatibilityLibraries(entries)
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
