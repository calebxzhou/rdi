package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.deleteRecursivelyNoSymlink
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.model.firstLoaderDir
import calebxzhou.rdi.client.model.loaderManifest
import calebxzhou.rdi.client.net.SERVER_NODES
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.proxy.LocalMcProxy.gameAddr
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.isDesktop
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.archive.PackArchiveFormat
import calebxzhou.rdi.common.archive.detectArchiveFormat
import calebxzhou.rdi.common.archive.extractArchiveToDir
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.util.ok
import calebxzhou.rdi.common.util.str
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.FileNotFoundException
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.atomic.AtomicBoolean

enum class LocalModpackSourceType {
    MODRINTH,
    CURSEFORGE
}

data class LoadedLocalModpack(
    val sourceType: LocalModpackSourceType,
    val sourceDir: File,
    val packName: String,
    val packVersion: String,
    val mcVersion: McVersion,
    val modloader: ModLoader,
    val mods: List<Mod>,
    val embeddedModOriginalFileNames: Map<String, String> = emptyMap()
)

/**
 * Common modpack download/install service.
 * Upload operations remain desktop-only in desktopMain.
 */
object ModpackService {

    fun modpackInstallTaskKey(modpackId: ObjectId, verName: String): String =
        "modpack-install:${modpackId.toHexString()}:$verName"

    suspend fun load(
        file: File,
        onProgress: (LoadProgress) -> Unit = {}
    ): Result<LoadedLocalModpack> = loadLocalModpack(
        file = file,
        onProgress = onProgress
    )

    fun getVersionDir(modpackId: ObjectId, verName: String): java.io.File {
        return ClientDirs.versionsDir.resolve("${modpackId}_${verName}")
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
        server.download("modpack/$modpackId/version/$verName/client", tempFile.absolutePath) { prog ->
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
        if (tempFile.sha1 != hash) {
            tempFile.delete()
            throw IllegalStateException("客户端包下载损坏，请重试")
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
            val currentVersion = if (deleteIncludedMods) {
                server.makeRequest<Modpack.Version>("modpack/${packdir.vo.id}/version/${packdir.verName}").data
                    ?: throw RequestError("未找到对应版本信息，无法删除关联Mod")
            } else null
            val currentModFileNames = currentVersion?.mods
                ?.map { it.fileName }
                ?.toSet()
                .orEmpty()
            val removableModFileNames = if (deleteIncludedMods && currentModFileNames.isNotEmpty()) {
                val referencedByOthers = collectReferencedModFileNamesExcluding(
                    packdir = packdir,
                    fallbackPreserve = currentModFileNames
                )
                currentModFileNames - referencedByOthers
            } else {
                emptySet()
            }

            deleteLocalPackDir(packdir.dir)

            removableModFileNames.forEach { fileName ->
                val modFile = DL_MOD_DIR.resolve(fileName)
                if (modFile.exists() && !modFile.delete()) {
                    throw IllegalStateException("无法删除Mod文件: ${modFile.absolutePath}")
                }
            }
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

    fun installVersion(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackId: ObjectId,
        verName: String,
        mods: List<Mod>
    ): Task {
        var clientPackFile: java.io.File? = null
        val versionDir = getVersionDir(modpackId, verName)

        val downloadModsTask = ModService.downloadModsTask(mods)

        val downloadClientPackTask = Task.Leaf("下载客户端整合包") { ctx ->
            val hash = server.makeRequest<String>("modpack/$modpackId/version/$verName/client/hash").data
                ?: throw IllegalStateException("客户端包hash为空")
            val cached = findCachedClientPackFile(modpackId, verName, hash)
            if (cached != null) {
                ctx.emitProgress(TaskProgress("客户端整合包已存在", 1f))
                clientPackFile = cached
                return@Leaf
            }
            ctx.emitProgress(TaskProgress("开始下载...", 0f))
            clientPackFile = downloadClientPackArchive(modpackId, verName, hash) { progress ->
                ctx.emitProgress(TaskProgress(progress.message, progress.fraction))
            }
            ctx.emitProgress(TaskProgress("下载完成", 1f))
        }

        val prepareVersionDirTask = Task.Leaf("准备安装目录") { ctx ->
            if (versionDir.exists()) {
                ctx.emitProgress(TaskProgress("清理旧版本文件...", null))
                runCatching { versionDir.deleteRecursivelyNoSymlink() }
                    .getOrElse { throw IllegalStateException("无法清理旧版本目录: ${versionDir.absolutePath}", it) }
            }
            if (!versionDir.exists()) {
                versionDir.mkdirs()
            }
            ctx.emitProgress(TaskProgress("目录已就绪", 1f))
        }

        val extractTask = Task.Leaf("解压客户端整合包") { ctx ->
            val clientPack = clientPackFile ?: throw IllegalStateException("客户端包未准备好")
            ctx.emitProgress(TaskProgress("扫描压缩包内容...", 0f))
            extractArchiveToDir(
                archiveFile = clientPack,
                targetDir = versionDir,
                pathTransform = ::normalizeInstalledClientPackPath
            ) { done, total, currentPath ->
                val fraction = done.toFloat() / total.coerceAtLeast(1).toFloat()
                ctx.emitProgress(TaskProgress("解压中 ${currentPath.substringAfterLast('/')}($done/$total)", fraction))
            }
            ctx.emitProgress(TaskProgress("解压完成", 1f))
        }

        val copyModsTask = Task.Leaf("复制mod文件") { ctx ->
            val modsDir = versionDir.resolve("mods").apply { mkdirs() }
            val modFiles = mods.map { mod ->
                val file = ClientDirs.dlModsDir.resolve(mod.fileName)
                if (!file.exists()) {
                    throw IllegalStateException("缺少Mod文件: ${file.absolutePath}")
                }
                file
            }
            modFiles.forEachIndexed { index, modFile ->
                val target = modsDir.resolve(modFile.name)
                linkOrCopyMod(modFile, target)
                val fraction = (index + 1).toFloat() / modFiles.size.coerceAtLeast(1)
                ctx.emitProgress(TaskProgress("已处理 ${index + 1}/${modFiles.size}", fraction))
            }

            installRdiCore(mcVersion, modLoader, modsDir)
            ctx.emitProgress(TaskProgress("完成", 1f))
        }

        val writeOptionsTask = Task.Leaf("写入配置文件") { ctx ->
            val optionsFile = versionDir.resolve("options.txt")
            optionsFile.writeText(
                mergeMinecraftOptions(
                    original = optionsFile.takeIf(File::exists)?.readText().orEmpty(),
                    overrides = linkedMapOf(
                        "lang" to "zh_cn",
                        "darkMojangStudiosBackground" to "true",
                        "forceUnicodeFont" to "true"
                    )
                )
            )
            try {
                versionDir.resolve(versionDir.name+".json").writeText(mcVersion.loaderManifest.copy(id=versionDir.name).json)
            } catch (e: FileNotFoundException) {
                throw RequestError("没有找到${mcVersion.mcVer}版本的${modLoader.name}，请先安装")
            }

            ctx.emitProgress(TaskProgress("写入完成", 1f))
        }

        return Task.Sequence(
            name = "安装整合包 $verName",
            subTasks = listOf(
                downloadModsTask,
                downloadClientPackTask,
                prepareVersionDirTask,
                extractTask,
                copyModsTask,
                writeOptionsTask
            )
        )
    }

    fun installVersionTask2(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackId: ObjectId,
        verName: String,
        mods: List<Mod>
    ): Task2 {
        var clientPackFile: File? = null
        val downloadModsTask = ModService.downloadModsTask2(mods)

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
            mods = mods
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
        val mcSlug = "${mcVersion.mcVer}-${modLoader.name.lowercase()}"
        val mcCoreSource = ClientDirs.dlModsDir.resolve("rdi-5-mc-client-$mcSlug.jar")
        val mcCoreTarget = modsDir.resolve("rdi-5-mc-client-$mcSlug.jar")
        if (mcCoreSource.exists()) {
            linkOrCopyMod(mcCoreSource, mcCoreTarget)
        } else {
            throw IllegalStateException("缺少核心文件: ${mcCoreSource.absolutePath}")
        }
    }


    fun Modpack.Version.startInstall(
        mcVersion: McVersion,
        modLoader: ModLoader,
        modpackName: String? = null
    ): Task {
        val title = buildString {
            append("完整下载整合包")
            if (!modpackName.isNullOrBlank()) append(" ").append(modpackName)
            totalSize?.humanFileSize?.let { append(" ").append(it) }
        }
        return Task.Sequence(
            name = title,
            subTasks = listOf(
                installVersion(mcVersion, modLoader, modpackId, this@startInstall.name, mods)
            )
        )
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

    suspend fun fetchSourceIntro(url: String) : Result<String>{
        return ok("")
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
                        ?.map { it.fileName }
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
        val versionDir = getVersionDir(modpackId, verName)
        val prepareVersionDirTask = Task2.Leaf("准备安装目录") { ctx ->
            if (versionDir.exists()) {
                ctx.emit(Task2Progress("清理旧版本文件...", null))
                runCatching { versionDir.deleteRecursivelyNoSymlink() }
                    .getOrElse { throw IllegalStateException("无法清理旧版本目录: ${versionDir.absolutePath}", it) }
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

        val copyModsTask = Task2.Leaf("复制mod文件") { ctx ->
            val modsDir = versionDir.resolve("mods").apply { mkdirs() }
            val modFiles = mods.map { mod ->
                val file = ClientDirs.dlModsDir.resolve(mod.fileName)
                if (!file.exists()) {
                    throw IllegalStateException("缺少Mod文件: ${file.absolutePath}")
                }
                mod to file
            }
            modFiles.forEachIndexed { index, (mod, modFile) ->
                val targetFileName = embeddedModOriginalFileNames[mod.fileName]
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('\\')
                    ?.takeIf { it.isNotBlank() }
                    ?: modFile.name
                val target = modsDir.resolve(targetFileName)
                linkOrCopyMod(modFile, target)
                val fraction = (index + 1).toFloat() / modFiles.size.coerceAtLeast(1)
                ctx.emit(Task2Progress("已处理 ${index + 1}/${modFiles.size}", fraction))
            }
            installRdiCore(mcVersion, modLoader, modsDir)
            ctx.emit(Task2Progress("完成", 1f))
        }

        val writeOptionsTask = Task2.Leaf("写入配置文件") { ctx ->
            val optionsFile = versionDir.resolve("options.txt")
            optionsFile.writeText(
                mergeMinecraftOptions(
                    original = optionsFile.takeIf(File::exists)?.readText().orEmpty(),
                    overrides = linkedMapOf(
                        "lang" to "zh_cn",
                        "darkMojangStudiosBackground" to "true",
                        "forceUnicodeFont" to "true"
                    )
                )
            )
            try {
                versionDir.resolve(versionDir.name + ".json")
                    .writeText(mcVersion.loaderManifest.copy(id = versionDir.name).json)
            } catch (e: FileNotFoundException) {
                throw RequestError("没有找到${mcVersion.mcVer}版本的${modLoader.name}，请先安装")
            }
            ctx.emit(Task2Progress("写入完成", 1f))
        }
        return listOf(prepareVersionDirTask, extractTask, copyModsTask, writeOptionsTask)
    }
}

private fun mergeMinecraftOptions(
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

// ---- Types and functions moved from desktopMain for cross-platform use ----

sealed class StartPlayResult {
    data class Ready(val args: McPlayArgs) : StartPlayResult()
    data class NeedMc(val ver: McVersion) : StartPlayResult()
    data class NeedInstall(val task: Task2) : StartPlayResult()
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
    val statusResp = server.makeRequest<HostStatus>("host/${_id}/status")
    val status = statusResp.data ?: throw RequestError("获取房间状态失败: ${statusResp.msg}")

    val versionResp = server.makeRequest<Modpack.Version>("modpack/${modpack.id}/version/$packVer")
    val version = versionResp.data ?: throw RequestError("获取整合包版本信息失败: ${versionResp.msg}")

    if (!(modpack.mcVer.firstLoaderDir.exists())) {
        return StartPlayResult.NeedMc(modpack.mcVer)
    }
    if (!ModpackService.isVersionInstalled(modpack.id, packVer)) {
        val task = with(ModpackService) { version.startInstallTask2(modpack.mcVer, modpack.modloader, modpack.name) }
        return StartPlayResult.NeedInstall(task)
    }

    when (status) {
        HostStatus.PLAYABLE, HostStatus.STARTED -> Unit
        HostStatus.STOPPED -> {
            val startResp = server.makeRequest<Unit>("host/${_id}/start", HttpMethod.Post)
            if (!startResp.ok) {
                throw RequestError("启动房间失败: ${startResp.msg}")
            }
        }
        else -> throw RequestError("房间状态未知，无法游玩")
    }

    if (GameService.started) {
        throw RequestError("mc运行中，如需切换要玩的房间，请先关闭mc")
    }
    var gameAddr = "127.0.0.1:55667"
    if(!isDesktop){
        val verDir = ModpackService.getVersionDir(version.modpackId,version.name)
        ModpackService.installRdiCore(modpack.mcVer,modpack.modloader,verDir)
        //安卓端暂时不支持本地代理
        gameAddr = (SERVER_NODES[CONF.carrier]?:SERVER_NODES[0])?.gameAddr!!
    }
    //val gameAddr = SERVER_NODES[CONF.carrier]?.gameAddr?:SERVER_NODES[0]
    val versionId = "${modpack.id.str}_${version.name}"
    val playArg = "${server.hqUrl}\n" +
            "${gameAddr}\n"+
            "${name}\n"+
            "$port\n"+
            "${loggedAccount.uuid}\n"+
            loggedAccount.name

    return StartPlayResult.Ready(
        McPlayArgs(
            title = "游玩 $name",
            mcVer = modpack.mcVer,
            versionId = versionId,
            playArg = playArg,
            extraMods = extraMods,
            manageHostExtraMods = true
        )
    )
}

suspend fun ModpackService.getLocalPackDirs(): List<ModpackLocalDir> = coroutineScope {
    val pattern = Regex("^([0-9a-fA-F]{24})_(.+)$")
    val dirs = GameService.versionListDir.listFiles()?.asSequence()
        ?.filter { it.isDirectory }
        ?.toList()
        ?: return@coroutineScope emptyList()
    val deferred = dirs.mapNotNull { dir ->
        val match = pattern.matchEntire(dir.name) ?: return@mapNotNull null
        val (idStr, verName) = match.destructured
        async {
            val vo = server.makeRequest<Modpack.BriefVo>("modpack/${idStr}/brief").data ?: Modpack.BriefVo()
            val createTime = runCatching {
                java.nio.file.Files.readAttributes(
                    dir.toPath(),
                    java.nio.file.attribute.BasicFileAttributes::class.java
                ).creationTime().toMillis()
            }.getOrElse { dir.lastModified() }
            ModpackLocalDir(dir, verName, vo, createTime)
        }
    }
    deferred.awaitAll()
}
