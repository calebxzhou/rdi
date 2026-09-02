package calebxzau.rdi.mcinstall

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.util.*
import calebxzau.rdi.mclaunch.hostNativeArch
import calebxzau.rdi.mclaunch.MinecraftArtifactDownloader
import calebxzau.rdi.mclaunch.MinecraftDownloadProgress
import calebxzau.rdi.mclaunch.model.*
import calebxzau.rdi.mclaunch.rulesAllow
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.model.LibraryOsArch.Companion.detectHostOs
import calebxzhou.rdi.common.net.DownloadProgress
import calebxzhou.rdi.common.net.LocalArtifactHashAlgorithm
import calebxzhou.rdi.common.net.LocalArtifactRequest
import calebxzhou.rdi.common.net.LocalArtifactReuse
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.runInline
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipFile

class McInstall(
    internal val environment: McInstallEnvironment,
) {
    private val lgr by Loggers
    private val loaderInstalls = mutableMapOf<String, CompletableDeferred<Result<Unit>>>()

    private val directories get() = environment.directories
    private val libsDir get() = directories.librariesDir
    private val assetsDir get() = directories.assetsDir
    private val assetIndexesDir get() = directories.assetIndexesDir
    private val assetObjectsDir get() = directories.assetObjectsDir
    val versionListDir get() = directories.versionsDir
    val artifactDownloader = MinecraftArtifactDownloader { label, artifact, target, onProgress ->
        downloadArtifact(label, artifact, target) { progress ->
            onProgress(
                MinecraftDownloadProgress(
                    bytesDownloaded = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    fraction = progress.fraction,
                )
            )
        }
    }

    private val McVersion.metadata: MojangVersionManifest
        get() = loadResource("mcmeta/$mcVer.json").bufferedReader().use { reader ->
            serdesJson.decodeFromString(reader.readText())
        }

    private fun loadResource(name: String) = environment.resourceLoader(name).getOrThrow()

    private fun exportResource(name: String, target: File) {
        loadResource(name).use { input ->
            target.parentFile?.mkdirs()
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private val hostOs = detectHostOs()
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
    private val VERIFIED_DOWNLOAD_MAX_ATTEMPTS = 6
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
        return if (environment.preferMirror()) {
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

    suspend fun prepareForLaunch(
        request: McLaunchPreparationRequest,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean = { false },
    ): Result<Unit> = runCatching {
        ensureDesktopLaunchLoader(
            mcVer = request.mcVersion,
            loader = request.loader,
            onProgress = onProgress,
            isCancelled = isCancelled,
        ).getOrThrow()
        if (isCancelled()) return@runCatching

        val versionDir = versionListDir.resolve(request.versionId)
        environment.launchPreparer.prepare(
            mcVersion = request.mcVersion,
            versionId = request.versionId,
            versionDir = versionDir,
            onProgress = onProgress,
        ).getOrThrow()
        if (isCancelled()) return@runCatching

        ensureDesktopLaunchAssets(request.mcVersion, onProgress).getOrThrow()
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


    internal fun downloadClientTask2(manifest: MojangVersionManifest): Task2 {
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
            ?: directories.mcDir.resolve("minecraft_server.${mcVerStr}.jar")
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
                path.startsWith("net/neoforged/") -> "https://maven.neoforged.net/releases"
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
    internal val MojangLibrary.file
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
            path.startsWith("net/neoforged/") -> "https://maven.neoforged.net/releases"
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
        if (reuseLocalArtifact(artifact.sha1, artifact.size, target)) {
            onProgress(DownloadProgress(target.length(), target.length(), 0.0))
            return Result.success(target)
        }
        if (sourcePlan.primaryUrls.isEmpty() && sourcePlan.fallbackUrls.isEmpty()) {
            throw IllegalStateException("$label 下载链接为空")
        }
        var attempt = 1
        var currentSourcePlan = sourcePlan
        while (true) {
            val result = target.toPath().downloadFileFrom(
                urls = currentSourcePlan.primaryUrls + currentSourcePlan.fallbackUrls,
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

    internal fun downloadLibrariesTask2(libraries: List<MojangLibrary>): Task2 {
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

    internal fun downloadAssetsTask2(manifest: MojangVersionManifest): Task2 {
        val assetIndexMeta = manifest.assetIndex ?: return Task2.Leaf("下载资源") { ctx ->
            ctx.emit(Task2Progress("找不到资源", 0f))
        }
        val plan = buildAssetRepairPlan(loadAssetIndex(assetIndexMeta))
        plan.toStub.forEach { (_, obj) -> writeEmptySoundStub(obj.hash) }

        val subTasks = plan.compacted.map { (path, obj) ->
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

        if (plan.linkPlans.isNotEmpty()) {
            val linksTask = Task2.Leaf("链接相似资源") { ctx ->
                plan.linkPlans.forEachIndexed { index, linkPlan ->
                    createObjectLink(linkPlan.fromHash, linkPlan.toHash)
                    ctx.emit(
                        Task2Progress(
                            "已链接 ${index + 1}/${plan.linkPlans.size}",
                            (index + 1).toFloat() / plan.linkPlans.size
                        )
                    )
                }
            }
            return Task2.Group(
                title = "下载${plan.compacted.size}个音频资源",
                children = subTasks + linksTask
            )
        }

        return Task2.Group(
            title = "下载${plan.compacted.size}个音频资源",
            children = subTasks
        )
    }

    suspend fun ensureDesktopLaunchAssets(
        mcVer: McVersion,
        onProgress: (String) -> Unit
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val assetIndexMeta = mcVer.metadata.assetIndex ?: return@withContext
            val plan = buildAssetRepairPlan(loadAssetIndex(assetIndexMeta))
            plan.toStub.forEach { (_, obj) -> writeEmptySoundStub(obj.hash) }
            val broken = plan.compacted.filter { (_, obj) -> !assetObjectValid(obj) }
            val missingLinks = plan.linkPlans.filter { !assetObjectExists(it.fromHash) }
            if (broken.isEmpty() && missingLinks.isEmpty()) {
                onProgress("资源完整")
                return@withContext
            }
            broken.forEach { (path, obj) ->
                onProgress("修复资源 $path")
                downloadAssetObject(path, obj) { progress ->
                    val total = progress.totalBytes.takeIf { it > 0 }?.humanFileSize ?: "未知"
                    onProgress("${path} ${progress.bytesDownloaded.humanFileSize}/$total")
                }.getOrThrow()
            }
            missingLinks.forEach { linkPlan -> createObjectLink(linkPlan.fromHash, linkPlan.toHash) }
            onProgress("资源已就绪")
        }
    }

    private fun loadAssetIndex(assetIndexMeta: MojangAssetIndex): MojangAssetIndexFile {
        val metaJson = loadResource("mcmeta/assets-index/${assetIndexMeta.id}.json").use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        if (!assetIndexesDir.exists()) {
            assetIndexesDir.mkdirs()
        }
        assetIndexesDir.resolve("${assetIndexMeta.id}.json").writeText(metaJson)
        return serdesJson.decodeFromString<MojangAssetIndexFile>(metaJson)
    }

    private data class AssetRepairPlan(
        val toStub: List<Map.Entry<String, MojangAssetObject>>,
        val compacted: List<Map.Entry<String, MojangAssetObject>>,
        val linkPlans: List<AssetLinkPlan>
    )

    private fun buildAssetRepairPlan(index: MojangAssetIndexFile): AssetRepairPlan {
        val toDownload = mutableListOf<Map.Entry<String, MojangAssetObject>>()
        val toStub = mutableListOf<Map.Entry<String, MojangAssetObject>>()
        index.objects.entries.forEach { entry ->
            when {
                shouldDownloadAsset(entry.key) -> toDownload += entry
                shouldUseEmptySound(entry.key) -> toStub += entry
                else -> Unit
            }
        }

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
        return AssetRepairPlan(toStub, compacted, linkPlans)
    }

    private fun assetObjectFile(hash: String): File {
        val h = hash.lowercase(Locale.ROOT)
        return assetObjectsDir.resolve(h.substring(0, 2)).resolve(h)
    }

    private fun assetObjectExists(hash: String): Boolean = assetObjectFile(hash).isFile

    private fun assetObjectValid(asset: MojangAssetObject): Boolean {
        val targetFile = assetObjectFile(asset.hash)
        if (!targetFile.isFile || targetFile.length() != asset.size) return false
        return runCatching { targetFile.sha1.equals(asset.hash, true) }.getOrDefault(false)
    }

    private suspend fun downloadAssetObject(
        path: String,
        asset: MojangAssetObject,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> {
        val hash = asset.hash.lowercase(Locale.ROOT)
        val targetFile = assetObjectFile(hash)
        val targetDir = targetFile.parentFile

        if (targetFile.exists()) {
            if (targetFile.length() == asset.size) {
                val existingSha = runCatching { targetFile.sha1 }.getOrNull()
                if (existingSha != null && existingSha.equals(hash, true)) {
                    return Result.success(targetFile)
                }
            }
        }

        targetDir?.mkdirs()
        if (reuseLocalArtifact(hash, asset.size, targetFile)) {
            onProgress(DownloadProgress(asset.size, asset.size, 0.0))
            return Result.success(targetFile)
        }
        val sourcePlan = buildAssetDownloadSourcePlan(hash)

        val maxRetries = 4
        var attempt = 0
        while (true) {
            val result = targetFile.toPath().downloadFileFrom(
                urls = sourcePlan.primaryUrls + sourcePlan.fallbackUrls,
                knownSize = asset.size,
                validator = { downloadedPath ->
                    if (
                        downloadedPath.toFile().length() == asset.size &&
                        downloadedPath.sha1.equals(hash, ignoreCase = true)
                    ) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("资源校验失败: $path"))
                    }
                }
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

    private fun MojangLibrary.isLoaderUniversalLibrary(): Boolean =
        name.substringBefore('@').split(':').getOrNull(3)?.equals("universal", ignoreCase = true) == true

    internal data class LoaderLibraryPlan(
        val launchManifest: MojangVersionManifest,
        val downloadLibraries: List<MojangLibrary>,
    )

    internal fun planLoaderLibraries(
        loaderManifest: MojangVersionManifest,
        installProfileLibraries: List<MojangLibrary>,
    ): LoaderLibraryPlan = LoaderLibraryPlan(
        launchManifest = loaderManifest,
        downloadLibraries = (installProfileLibraries + loaderManifest.libraries).distinctBy(::libraryKey),
    )

    internal fun selectLoaderLaunchManifest(
        existingManifest: MojangVersionManifest,
        installerManifest: MojangVersionManifest?,
    ): MojangVersionManifest = installerManifest ?: existingManifest

    private fun loaderLibrariesReady(libraries: List<MojangLibrary>): Boolean =
        libraries
            .filter { it.shouldDownloadByArch() }
            .flatMap { library ->
                buildList {
                    library.mainArtifact()?.let { artifact -> add(library to artifact) }
                    library.nativeArtifact()?.let { artifact -> add(library to artifact) }
                }
            }
            .all { (library, artifact) ->
                val path = artifact.path?.takeIf(String::isNotBlank)
                    ?: runCatching { descriptorToLibraryPath(library.name) }.getOrNull()
                    ?: return@all false
                val file = libsDir.resolve(path)
                if (!file.isFile || file.length() <= 0L) {
                    false
                } else {
                    val expectedSha1 = artifact.sha1.trim()
                    if (expectedSha1.isBlank()) {
                        runCatching { ZipFile(file).use { it.entries().hasMoreElements() } }.getOrDefault(false)
                    } else {
                        runCatching { file.sha1.equals(expectedSha1, ignoreCase = true) }.getOrDefault(false)
                    }
                }
            }

    private fun loaderInstallProfileReady(installProfile: LoaderInstallProfile): Boolean {
        if (!loaderLibrariesReady(installProfile.libraries.filter { it.isLoaderUniversalLibrary() })) return false
        return listOf("MC_SRG", "PATCHED")
            .mapNotNull { key -> extractLibraryDescriptor(installProfile.data[key]?.client) }
            .map(::descriptorToLibraryPath)
            .map(libsDir::resolve)
            .all { file ->
                file.isFile && file.length() > 0L &&
                    runCatching { ZipFile(file).use { it.entries().hasMoreElements() } }.getOrDefault(false)
            }
    }

    @Serializable
    internal data class LoaderInstallProfile(
        val serverJarPath: String? = null,
        val libraries: List<MojangLibrary> = emptyList(),
        val data: Map<String, LoaderInstallData> = emptyMap(),
        val versionInfo: MojangVersionManifest? = null,
        val install: LoaderLegacyInstall? = null,
        val json: String? = null,
    )

    @Serializable
    internal data class LoaderInstallData(
        val client: String? = null,
        val server: String? = null,
    )

    @Serializable
    internal data class LoaderLegacyInstall(
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
        val targetFile = assetObjectFile(hash)
        if (targetFile.isFile && targetFile.isValidEmptySoundStub()) return
        targetFile.parentFile?.mkdirs()
        loadResource("assets/empty.ogg").use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun File.isValidEmptySoundStub(): Boolean {
        val expected = loadResource("assets/empty.ogg").use { it.readBytes() }
        return length() == expected.size.toLong() && sha1 == expected.sha1Hex()
    }

    private fun ByteArray.sha1Hex(): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(this))

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
        val holder = LoaderInstallHolder(
            version = version,
            loader = loader,
            loaderVersionManifest = version.metadata,
        )
        return Task2.Sequence(
            title = "安装 $loader",
            children = listOf(
                Task2.Leaf("下载$loader 安装器") { ctx ->
                    prepareInstallerTask2(holder, ctx)
                },
                Task2.Leaf("解析安装器") { ctx ->
                    parseInstallerTask2(holder, ctx)
                },
                Task2.Leaf("下载$loader 依赖") { ctx ->
                    downloadLibrariesTask2(holder.loaderLibraries, ctx, holder.installer)
                },
                Task2.Leaf("下载客户端Mojmap") { ctx ->
                    downloadMojmapIfNeededTask2(holder, ctx, server = false)
                },
                Task2.Leaf("运行安装器") { ctx ->
                    runInstallerBootstrapperTask2(holder, ctx)
                },
            ),
        )
    }

    suspend fun ensureDesktopLaunchLoader(
        mcVer: McVersion,
        loader: ModLoader,
        onProgress: (String) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): Result<Unit> {
        val loaderVersion = mcVer.loaderVersions[loader]
            ?: return Result.failure(IllegalStateException("未配置${loader}安装信息"))
        val versionJson = versionListDir.resolve(loaderVersion.dirName).resolve("${loaderVersion.dirName}.json")
        readLoaderLaunchState(mcVer, loader, versionJson)?.let { state ->
            if (loaderLibrariesReady(state.manifest.libraries) && loaderInstallProfileReady(state.installProfile)) {
                return Result.success(Unit)
            }
        }
        val key = "${mcVer.mcVer}|${loader.name}"
        val pending = CompletableDeferred<Result<Unit>>()
        val active = synchronized(loaderInstalls) {
            loaderInstalls[key] ?: pending.also { loaderInstalls[key] = it }
        }
        if (active === pending) {
            val result = runCatching {
                onProgress("开始自动安装${loader}...")
                withContext(Dispatchers.IO) {
                    downloadLoaderTask2(mcVer, loader).runInline(
                        Task2Context(
                            isCancelled = isCancelled,
                            emitProgress = { progress ->
                                progress.message.takeIf(String::isNotBlank)?.let(onProgress)
                            }
                        )
                    )
                }
            }
            pending.complete(result)
            synchronized(loaderInstalls) {
                if (loaderInstalls[key] === pending) loaderInstalls.remove(key)
            }
        } else {
            onProgress("等待相同${loader}安装完成...")
        }
        return active.await().mapCatching {
            val state = readLoaderLaunchState(mcVer, loader, versionJson)
                ?: error("${loader}安装后缺少有效启动manifest")
            check(loaderLibrariesReady(state.manifest.libraries) && loaderInstallProfileReady(state.installProfile)) {
                "${loader}安装后运行库仍不完整"
            }
        }
    }

    private data class LoaderLaunchState(
        val manifest: MojangVersionManifest,
        val installProfile: LoaderInstallProfile,
    )

    private fun readLoaderLaunchState(
        mcVer: McVersion,
        loader: ModLoader,
        versionJson: File,
    ): LoaderLaunchState? {
        if (!versionJson.isFile) return null
        val existingManifest = runCatching {
            serdesJson.decodeFromString<MojangVersionManifest>(versionJson.readText())
        }.getOrNull() ?: return null
        val installer = directories.mcDir.resolve("${mcVer.mcVer}-${loader}-installer.jar")
        if (!installer.isFile) return null
        val installProfile = readInstallerEntryOrNull(installer, "install_profile.json")
            ?.let { runCatching { serdesJson.decodeFromString<LoaderInstallProfile>(it) }.getOrNull() }
            ?: return null
        val installerManifest = readInstallerEntryOrNull(installer, "version.json")
            ?.let { runCatching { serdesJson.decodeFromString<MojangVersionManifest>(it) }.getOrNull() }
        val launchManifest = selectLoaderLaunchManifest(existingManifest, installerManifest).normalizeLoaderManifest(
            LoaderInstallHolder(
                version = mcVer,
                loader = loader,
                loaderVersionManifest = mcVer.metadata,
            ),
            installProfile,
        )
        if (launchManifest.json != versionJson.readText()) {
            versionJson.writeText(launchManifest.json)
        }
        return LoaderLaunchState(launchManifest, installProfile)
    }

    fun downloadTestServerTask2(version: McVersion, loader: ModLoader): Task2 {
        val holder = LoaderInstallHolder(
            version = version,
            loader = loader,
            loaderVersionManifest = version.metadata,
        )
        return Task2.Sequence(
            title = "下载测试服务端 ${version.mcVer} $loader",
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
                Task2.Leaf("下载服务端Mojmap") { ctx ->
                    downloadMojmapIfNeededTask2(holder, ctx, server = true)
                },
                Task2.Leaf("运行安装器服务端") { ctx ->
                    runServerInstallerBootstrapperTask2(holder, ctx)
                }
            )
        )
    }


    internal data class LoaderInstallHolder(
        val version: McVersion,
        val loader: ModLoader,
        var installer: File? = null,
        var installBooter: File? = null,
        var loaderVersionManifest: MojangVersionManifest,
        var installProfile: LoaderInstallProfile? = null,
        var loaderLibraries: List<MojangLibrary> = emptyList(),
        var clientInstallerAlreadyHandled: Boolean = false,
        var serverInstallerAlreadyHandled: Boolean = false,
    )

    private suspend fun prepareInstallerTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        val loaderMeta = holder.version.loaderVersions[holder.loader]
            ?: error("未配置 ${holder.loader} 安装器下载链接")
        val mcDir = directories.mcDir
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

        if (reuseLocalArtifact(loaderMeta.installerSha1, null, installer)) {
            ctx.emit(Task2Progress("从本地复用安装器", 1f))
            return
        }

        ctx.emit(Task2Progress("开始下载...", 0f))
        val sourcePlan = buildDownloadSourcePlan(loaderMeta.installerUrl)
        installer.toPath().downloadFileFrom(
            urls = sourcePlan.primaryUrls + sourcePlan.fallbackUrls,
            validator = { path ->
                val actualSha1 = path.sha1
                if (actualSha1 == loaderMeta.installerSha1) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("安装器校验失败"))
                }
            }
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
        val libraryPlan = planLoaderLibraries(loaderVersionManifest, installProfile.libraries)
        val loaderVersionDir = versionListDir.resolve(loaderVersionManifest.id).apply { mkdirs() }
        File(loaderVersionDir, "${loaderVersionManifest.id}.json").writeText(libraryPlan.launchManifest.json)

        holder.loaderVersionManifest = libraryPlan.launchManifest
        holder.installProfile = installProfile
        holder.loaderLibraries = libraryPlan.downloadLibraries

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

    private suspend fun downloadMojmapIfNeededTask2(
        holder: LoaderInstallHolder,
        ctx: Task2Context,
        server: Boolean,
    ) {
        val installProfile = holder.installProfile ?: return
        val vanillaManifest = holder.version.metadata
        val mojmaps = installProfile.data["MOJMAPS"] ?: return
        val downloads = vanillaManifest.downloads ?: return
        val descriptor = extractLibraryDescriptor(if (server) mojmaps.server else mojmaps.client)
        val artifact = if (server) downloads.serverMappings else downloads.clientMappings
        if (descriptor == null || artifact == null) {
            ctx.emit(Task2Progress("无需下载", 1f))
            return
        }
        val label = if (server) "服务端" else "客户端"
        val target = File(libsDir, descriptorToLibraryPath(descriptor))
        downloadArtifact(label, artifact, target) { progress ->
            ctx.emit(
                Task2Progress(
                    "$label ${progress.bytesDownloaded.humanFileSize}/${progress.totalBytes.humanFileSize}",
                    progress.fraction
                )
            )
        }.getOrThrow()
        ctx.emit(Task2Progress("$label Mojmap已完成", 1f))
    }

    internal fun runInstallerBootstrapperTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        if (holder.clientInstallerAlreadyHandled) {
            ctx.emit(Task2Progress("旧版Forge安装已完成", 1f))
            return
        }
        runInstallerBootstrapper(holder, ctx.asLegacyTaskContext())
    }

    internal fun runServerInstallerBootstrapperTask2(holder: LoaderInstallHolder, ctx: Task2Context) {
        if (holder.serverInstallerAlreadyHandled) {
            ctx.emit(Task2Progress("旧版Forge服务端安装已完成", 1f))
            return
        }
        runServerInstallerBootstrapper(holder, ctx.asLegacyTaskContext())
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
        return downloadVerifiedArtifact(
            label = label,
            artifact = artifact,
            target = target,
            sourcePlan = sourcePlan,
            onProgress = onProgress
        )
    }

    private suspend fun reuseLocalArtifact(expectedSha1: String, expectedSize: Long?, target: File): Boolean {
        if (expectedSha1.isBlank()) return false
        val targetPath = target.toPath().toAbsolutePath().normalize()
        val mcRoot = directories.mcDir.toPath().toAbsolutePath().normalize()
        val relativePaths = if (targetPath.startsWith(mcRoot)) {
            listOf(mcRoot.relativize(targetPath).toString())
        } else {
            emptyList()
        }
        val source = LocalArtifactReuse.reuse(
            LocalArtifactRequest(
                algorithm = LocalArtifactHashAlgorithm.SHA1,
                hash = expectedSha1,
                size = expectedSize?.takeIf { it > 0 },
                relativePaths = relativePaths
            ),
            targetPath
        ).onFailure {
            lgr.warn(it) { "查找本地Minecraft文件失败，将使用网络下载：${target.name}" }
        }.getOrNull()
        if (source != null) lgr.info { "从本地复用Minecraft文件：$source -> $target" }
        return source != null
    }

    // Minecraft launch argument and classpath resolution lives in client/mclaunch.

}
