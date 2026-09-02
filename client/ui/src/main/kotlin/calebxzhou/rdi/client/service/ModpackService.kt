package calebxzhou.rdi.client.service

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.util.humanFileSize
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.service.ModpackService.startInstallTask2
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzau.rdi.client.ui.loadResourceStream
import calebxzhou.rdi.common.archive.PackArchiveFormat
import calebxzhou.rdi.common.archive.detectArchiveFormat
import calebxzhou.rdi.common.archive.extractArchiveToDir
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.net.json
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.util.str
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

private val lgr by Loggers

/** Modpack download and installation service. */
object ModpackService {

    fun modpackInstallTaskKey(modpackId: ObjectId, verName: String): String =
        "modpack-install:${modpackId.toHexString()}:$verName"

    fun getVersionDir(modpackId: ObjectId, verName: String): java.io.File {
        return ClientDirs.versionsDir.resolve("${modpackId}_${verName}")
    }

    suspend fun getBriefInfos(ids: List<ObjectId>): Result<List<Modpack.BriefVo>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        return try {
            val response = server.makeRequest<List<Modpack.BriefVo>>("modpack/infos", HttpMethod.Post) {
                json()
                setBody(ids.json)
            }
            if (!response.ok) throw RequestError(response.msg)
            Result.success(response.data.orEmpty())
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            lgr.warn(cause) { "批量获取整合包简介失败" }
            Result.failure(cause)
        }
    }

    private fun clientPackZipFile(modpackId: ObjectId, verName: String): File =
        ClientDirs.dlPacksDir.resolve("${modpackId}_$verName.zip")

    private fun clientPackTarZstFile(modpackId: ObjectId, verName: String): File =
        ClientDirs.dlPacksDir.resolve("${modpackId}_$verName.tar.zst")

    private fun clientPackTempFile(modpackId: ObjectId, verName: String): File =
        ClientDirs.dlPacksDir.resolve("${modpackId}_$verName.download")

    private fun normalizeInstalledClientPackPath(path: String): String? {
        val normalized = path.replace('\\', '/').trimStart('/')
        return normalized.removePrefix("overrides/").takeIf { it.isNotBlank() }
    }

    private fun findCachedClientPackFile(modpackId: ObjectId, verName: String, hash: String): File? =
        listOf(
            clientPackTarZstFile(modpackId, verName),
            clientPackZipFile(modpackId, verName)
        ).firstOrNull { it.exists() && it.sha1 == hash }

    private suspend fun downloadClientPackArchive(
        modpackId: ObjectId,
        verName: String,
        hash: String,
        onProgress: (Task2Progress) -> Unit
    ): File {
        ClientDirs.dlPacksDir.mkdirs()
        findCachedClientPackFile(modpackId, verName, hash)?.let { return it }
        val tempFile = clientPackTempFile(modpackId, verName)
        if (tempFile.exists()) tempFile.delete()
        server.download(
            path = "modpack/$modpackId/version/$verName/client",
            saveTo = tempFile.absolutePath,
            validator = { path ->
                val actualHash = path.sha1
                if (actualHash == hash) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("客户端包下载损坏，请重试"))
                }
            }
        ) { prog ->
            val fraction = if (prog.totalBytes > 0) {
                prog.bytesDownloaded.toFloat() / prog.totalBytes
            } else {
                null
            }
            val msg = if (prog.totalBytes > 0) {
                "${prog.bytesDownloaded.humanFileSize}/${prog.totalBytes.humanFileSize}"
            } else {
                prog.bytesDownloaded.humanFileSize
            }
            onProgress(Task2Progress(msg, fraction))
        }
        val target = when (tempFile.detectArchiveFormat()) {
            PackArchiveFormat.TAR_ZST -> clientPackTarZstFile(modpackId, verName)
            PackArchiveFormat.ZIP -> clientPackZipFile(modpackId, verName)
        }
        if (target.exists()) target.delete()
        if (!tempFile.renameTo(target)) {
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
        }
        val staleSibling = if (target == clientPackTarZstFile(modpackId, verName)) {
            clientPackZipFile(modpackId, verName)
        } else {
            clientPackTarZstFile(modpackId, verName)
        }
        if (staleSibling.exists()) staleSibling.delete()
        return target
    }

    suspend fun deleteLocalPack(
        packdir: ModpackLocalDir,
        deleteIncludedMods: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            /*
            TODO bug cannot del symlink target mod
             可以复现方向基本明确。deleteLocalPack(..., deleteIncludedMods = true) 现在比不勾选多做了几件高风险事：

  1. 请求当前版本信息：
     client/ui/src/main/kotlin/calebxzhou/rdi/client/service/ModpackService.kt:159

     server.makeRequest<Modpack.Version>("modpack/${packdir.vo.id}/version/${packdir.verName}")

     如果这个本地包对应的远程版本已经删除/改名/网络失败，会直接 throw RequestError，导致本地目录完全不删。*/
            ModpackLaunchOptionsService.delete(packdir.versionId).getOrThrow()
            deleteLocalPackDir(packdir.dir)
        }
    }

    private fun deleteLocalPackDir(dir: File) {
        if (!dir.exists()) return
        Files.walk(dir.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
        }
    }

    fun installVersionTask2(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackId: ObjectId,
        verName: String,
        mods: List<Mod>
    ): Task2 {
        var clientPackFile: File? = null
        val installableMods = mods.filter(::isClientInstallableMod)
        val downloadModsTask = ModService.downloadModsTask2(installableMods)

        val downloadClientPackTask = Task2.Leaf("下载客户端整合包") { ctx ->
            val hash = server.makeRequest<String>("modpack/$modpackId/version/$verName/client/hash").data
                ?: throw IllegalStateException("客户端包hash为空")
            val cached = findCachedClientPackFile(modpackId, verName, hash)
            if (cached != null) {
                ctx.emit(Task2Progress("客户端整合包已存在", 1f))
                clientPackFile = cached
            } else {
                ctx.emit(Task2Progress("开始下载...", 0f))
                clientPackFile = downloadClientPackArchive(modpackId, verName, hash) { progress ->
                    ctx.emit(progress)
                }
                ctx.emit(Task2Progress("下载完成", 1f))
            }
        }
        val localInstallTasks = createInstallClientZipTasks2(
            mcVersion = mcVersion,
            modLoader = modLoader,
            modpackId = modpackId,
            verName = verName,
            mods = installableMods
        ) {
            clientPackFile ?: throw IllegalStateException("客户端包未准备好")
        }

        return Task2.Sequence(
            title = "安装整合包 $verName",
            children = listOf(
                downloadModsTask,
                downloadClientPackTask,
                *localInstallTasks.toTypedArray()
            )
        )
    }

    fun installBuiltClientZipTask2(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackId: ObjectId,
        verName: String,
        mods: List<Mod>,
        clientPackFile: File,
        modpackName: String? = null,
        embeddedModOriginalFileNames: Map<String, String> = emptyMap()
    ): Task2 = Task2.Sequence(
        title = buildString {
            append("本地安装整合包")
            if (!modpackName.isNullOrBlank()) append(" ").append(modpackName)
        },
        children = createInstallClientZipTasks2(
            mcVersion = mcVersion,
            modLoader = modLoader,
            modpackId = modpackId,
            verName = verName,
            mods = mods,
            embeddedModOriginalFileNames = embeddedModOriginalFileNames
        ) { clientPackFile }
    )

    fun installRdiCore(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modsDir: File
    ) {
        check(installCachedRdiCore(mcVersion, modLoader, modsDir)) {
            "缺少核心文件: ${McCoreUpdater.cacheFile(mcVersion, modLoader).absolutePath}"
        }
    }

    private fun installCachedRdiCore(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modsDir: File
    ): Boolean {
        val mcCoreSource = McCoreUpdater.cacheFile(mcVersion, modLoader)
        if (!mcCoreSource.isFile) return false
        hardLinkFile(mcCoreSource, modsDir.resolve(mcCoreSource.name)).getOrThrow()
        return true
    }


    fun Modpack.Version.startInstallTask2(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackName: String? = null
    ): Task2 {
        val title = buildString {
            append("完整下载整合包")
            if (!modpackName.isNullOrBlank()) append(" ").append(modpackName)
            totalSize?.humanFileSize?.let { append(" ").append(it) }
        }
        return Task2.Sequence(
            title = title,
            children = listOf(
                Task2.Leaf("刷新节点") { ctx ->
                    ctx.emit(Task2Progress("正在刷新节点...", 0f))
                    NodeRefreshCoordinator.refreshCurrent()
                        .onSuccess { ctx.emit(Task2Progress("节点已刷新: ${it.nodeName}", 1f)) }
                        .onFailure {
                            lgr.warn(it) { "下载整合包前刷新节点失败，继续使用当前节点" }
                            ctx.emit(Task2Progress("节点刷新失败，继续使用当前节点", 1f))
                        }
                },
                installVersionTask2(mcVersion, modLoader, modpackId, this@startInstallTask2.name, mods)
            )
        )
    }

    fun isVersionInstalled(modpackId: ObjectId, verName: String): Boolean {
        val versionDir = getVersionDir(modpackId, verName)
        if (!versionDir.exists()) return false
        versionDir.resolve("mods").takeIf { it.exists() } ?: return false
        return true
    }

    fun isVersionReadyToLaunch(version: Modpack.Version, activeMods: List<Mod>): Boolean {
        val versionDir = getVersionDir(version.modpackId, version.name)
        return versionDir.exists() && versionDir.resolve("mods").isDirectory
    }

    private suspend fun collectReferencedModFileNamesExcluding(
        packdir: ModpackLocalDir,
        fallbackPreserve: Set<String>
    ): Set<String> = coroutineScope {
        val otherLocalDirs = getLocalPackDirs().filter { it.versionId != packdir.versionId }
        if (otherLocalDirs.isEmpty()) return@coroutineScope emptySet()
        val failed = AtomicBoolean(false)
        val references = otherLocalDirs.map { other ->
            async {
                runCatching {
                    server.makeRequest<Modpack.Version>("modpack/${other.vo.id}/version/${other.verName}").data
                        ?.mods
                        ?.flatMap { it.fileNames }
                        ?.toSet()
                        .orEmpty()
                }.getOrElse {
                    failed.set(true)
                    emptySet()
                }
            }
        }.awaitAll().flatten().toSet()
        if (failed.get()) fallbackPreserve else references
    }

    private fun createInstallClientZipTasks2(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackId: ObjectId,
        verName: String,
        mods: List<Mod>,
        embeddedModOriginalFileNames: Map<String, String> = emptyMap(),
        clientPackProvider: () -> File
    ): List<Task2> {
        val installableMods = mods.filter(::isClientInstallableMod)
        val versionDir = getVersionDir(modpackId, verName)
        val prepareVersionDirTask = Task2.Leaf("准备安装目录") { ctx ->
            if (versionDir.exists()) {
                ctx.emit(Task2Progress("清理旧版本文件...", null))
                runCatching {
                    versionDir.deleteRecursivelyNoSymlink()
                }.getOrElse { throw IllegalStateException("无法清理旧版本目录: ${versionDir.absolutePath}", it) }
            }
            if (!versionDir.exists()) {
                versionDir.mkdirs()
            }
            ctx.emit(Task2Progress("目录已就绪", 1f))
        }

        val extractTask = Task2.Leaf("解压客户端整合包") { ctx ->
            val clientPack = clientPackProvider()
            ctx.emit(Task2Progress("扫描压缩包内容...", 0f))
            extractArchiveToDir(
                archiveFile = clientPack,
                targetDir = versionDir,
                pathTransform = ::normalizeInstalledClientPackPath
            ) { done, total, currentPath ->
                val fraction = done.toFloat() / total.coerceAtLeast(1).toFloat()
                ctx.emit(Task2Progress("解压中 ${currentPath.substringAfterLast('/')}($done/$total)", fraction))
            }
            ctx.emit(Task2Progress("解压完成", 1f))
        }

        val patchFancyMenuTask = Task2.Leaf("写入菜单") { ctx ->
            patchFancyMenuOptions(versionDir)
            ctx.emit(Task2Progress("写入完成", 1f))
        }

        val copyModsTask = Task2.Leaf("复制mod文件") { ctx ->
            val modsDir = versionDir.resolve("mods").apply { mkdirs() }
            val modFiles = installableMods.map { mod ->
                val file = mod.candidateFiles.firstOrNull(File::exists)
                    ?: throw IllegalStateException("缺少Mod文件: ${mod.targetFile.absolutePath}")
                mod to file
            }
            modFiles.forEachIndexed { index, (mod, modFile) ->
                val targetFileName = if (mod.fileName != mod.legacyFileName) {
                    mod.fileName
                } else {
                    embeddedModOriginalFileNames[mod.fileName]
                        ?.substringAfterLast('/')
                        ?.substringAfterLast('\\')
                        ?.takeIf { it.isNotBlank() }
                        ?: modFile.name
                }
                val target = modsDir.resolve(targetFileName)
                hardLinkFile(modFile, target).getOrThrow()
                val fraction = (index + 1).toFloat() / modFiles.size.coerceAtLeast(1)
                ctx.emit(Task2Progress("已处理 ${index + 1}/${modFiles.size}", fraction))
            }
            installCachedRdiCore(mcVersion, modLoader, modsDir)
            ctx.emit(Task2Progress("完成", 1f))
        }

        val writeOptionsTask = Task2.Leaf("写入配置文件") { ctx ->
            writeOptions(versionDir, mcVersion)
            ctx.emit(Task2Progress("写入完成", 1f))
        }
        return listOf(prepareVersionDirTask, extractTask, patchFancyMenuTask, copyModsTask, writeOptionsTask)
    }

    private fun patchFancyMenuOptions(versionDir: File) {
        val optionsFile = versionDir.resolve("config/fancymenu/options.txt")
        optionsFile.parentFile?.mkdirs()
        loadResourceStream("overrides/fancymenu-options.txt").use { input ->
            optionsFile.outputStream().use(input::copyTo)
        }
    }

    fun writeOptions(versionDir: File, mcVersion: McVersion) {
        val optionsFile = versionDir.resolve("options.txt")
        val overrides = linkedMapOf<String, String>().apply {
            when (mcVersion) {
                McVersion.V211,
                McVersion.V201,
                    -> {
                    put("darkMojangStudiosBackground", "true")
                    put("lang", "zh_cn")
                }

                McVersion.V122 -> {
                    put("lang", "zh_cn")
                }

                McVersion.V071 -> {
                    put("lang", "zh_CN")
                }
            }
            put("forceUnicodeFont", "true")
        }
        optionsFile.writeText(
            mergeMinecraftOptions(
                original = optionsFile.takeIf(File::exists)?.readText().orEmpty(),
                overrides = overrides
            )
        )
    }
}

fun mergeMinecraftOptions(
    original: String,
    overrides: Map<String, String>
): String {
    if (original.isBlank()) {
        return overrides.entries.joinToString("\n") { (key, value) -> "$key:$value" }
    }

    val lineSeparator = if ("\r\n" in original) "\r\n" else "\n"
    val updatedKeys = linkedSetOf<String>()
    val mergedLines = original.lineSequence().map { line ->
        val delimiterIndex = line.indexOf(':')
        if (delimiterIndex <= 0) {
            return@map line
        }
        val key = line.substring(0, delimiterIndex)
        val overrideValue = overrides[key] ?: return@map line
        updatedKeys += key
        "$key:$overrideValue"
    }.toMutableList()

    overrides.forEach { (key, value) ->
        if (key !in updatedKeys) {
            mergedLines += "$key:$value"
        }
    }

    return mergedLines.joinToString(lineSeparator)
}

// ---- Local modpack loading ----

sealed class StartPlayResult {
    data class Ready(val args: McPlayArgs) : StartPlayResult()
    data class NeedMc(val ver: McVersion) : StartPlayResult()
    data class NeedMod(val modSlugs: List<String>) : StartPlayResult()
    data class NeedInstall(val task: Task2, val dedupeKey: String) : StartPlayResult()
    data class Installing(val runId: String) : StartPlayResult()
}

data class ModpackLocalDir(
    val dir: java.io.File,
    val verName: String,
    val vo: Modpack.BriefVo,
    val createTime: Long
) {
    val versionId = dir.name
}

suspend fun Host.DetailVo.startPlay(): StartPlayResult {
    NodeRefreshCoordinator.refreshCurrent()
        .onFailure { lgr.warn(it) { "启动游戏前刷新节点失败，继续使用当前节点" } }

    val statusResp = server.makeRequest<HostStatus>("host/${_id}/status")
    val status = statusResp.data ?: throw RequestError("获取房间状态失败: ${statusResp.msg}")

    val versionResp = server.makeRequest<Modpack.Version>("modpack/${modpack.id}/version/$packVer")
    val version = versionResp.data ?: throw RequestError("获取整合包版本信息失败: ${versionResp.msg}")
    val installTaskKey = ModpackService.modpackInstallTaskKey(version.modpackId, version.name)
    ClientTaskManager.entries.value.firstOrNull { entry ->
        entry.dedupeKey == installTaskKey && !entry.status.isTerminal
    }?.let { activeInstall ->
        return StartPlayResult.Installing(activeInstall.runId)
    }

    val activeBaseMods = version.mods
        .filterNot { versionMod -> disabledMods.any { sameMod(it, versionMod) } }
        .filter(::isClientInstallableMod)
    if (!ModpackService.isVersionReadyToLaunch(version, activeBaseMods)) {
        return StartPlayResult.NeedInstall(
            task = version.startInstallTask2(modpack.mcVer, modpack.modloader, modpack.name),
            dedupeKey = installTaskKey
        )
    }

    val startResp = server.makeRequest<Unit>("host/${_id}/start", HttpMethod.Post)
    if (!startResp.ok) {
        throw RequestError("启动房间失败: ${startResp.msg}")
    }

    val gameAddr = "127.0.0.1:55667"
    val versionId = "${modpack.id.str}_${version.name}"
    val playArg = "${server.hqUrl}\n" +
            "${gameAddr}\n" +
            "${name}\n" +
            "$port\n" +
            "${loggedAccount.uuid}\n" +
            loggedAccount.name
    lgr.info { "play arg: $playArg" }
    runCatching {
        server.makeRequest<Unit>("modpack/${modpack.id}/play", HttpMethod.Post)
    }

    return StartPlayResult.Ready(
        McPlayArgs(
            title = "游玩 $name",
            mcVer = modpack.mcVer,
            modLoader = modpack.modloader,
            versionId = versionId,
            playArg = playArg,
            modpackName = modpack.name,
            versionDir = ModpackService.getVersionDir(version.modpackId, version.name).absolutePath,
            activeBaseMods = activeBaseMods,
            disabledBaseMods = disabledMods,
            manageHostBaseMods = true,
            extraMods = extraMods,
            manageHostExtraMods = true
        )
    )
}

private fun isClientInstallableMod(mod: Mod): Boolean =
    mod.side != Mod.Side.SERVER && mod.side != Mod.Side.UNKNOWN

private data class LocalPackRef(
    val dir: File,
    val modpackId: ObjectId,
    val verName: String
)

suspend fun ModpackService.getLocalPackDirs(): List<ModpackLocalDir> {
    val pattern = Regex("^([0-9a-fA-F]{24})_(.+)$")
    val dirs = GameService.versionListDir.listFiles()?.asSequence()
        ?.filter { it.isDirectory }
        ?.toList()
        ?: return emptyList()
    val refs = dirs.mapNotNull { dir ->
        pattern.matchEntire(dir.name)?.destructured?.let { (idStr, verName) ->
            LocalPackRef(dir, ObjectId(idStr), verName)
        }
    }

    if (refs.isEmpty()) return emptyList()
    val briefs = ModpackService.getBriefInfos(refs.map { it.modpackId }.distinct()).getOrElse {
        lgr.warn(it) { "读取本地整合包元数据失败，将跳过本地整合包" }
        return emptyList()
    }
    val briefsById = briefs.associateBy { it.id }
    return refs.mapNotNull { ref ->
        val vo = briefsById[ref.modpackId]
        if (vo == null) {
            lgr.warn { "本地整合包${ref.dir.name}在服务器不存在，将跳过该目录" }
            return@mapNotNull null
        }
        val createTime = runCatching {
            java.nio.file.Files.readAttributes(
                ref.dir.toPath(),
                java.nio.file.attribute.BasicFileAttributes::class.java
            ).creationTime().toMillis()
        }.getOrElse { ref.dir.lastModified() }
        ModpackLocalDir(ref.dir, ref.verName, vo, createTime)
    }
}
