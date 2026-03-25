package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.openChineseZip
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.model.firstLoaderDir
import calebxzhou.rdi.client.model.loaderManifest
import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.McPlayArgs
import calebxzhou.rdi.client.ui.isDesktop
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
import kotlinx.io.files.FileNotFoundException
import org.bson.types.ObjectId
import java.io.File

/**
 * Common modpack download/install service.
 * Upload operations remain desktop-only in desktopMain.
 */
object ModpackService {

    fun getVersionDir(modpackId: ObjectId, verName: String): java.io.File {
        return ClientDirs.versionsDir.resolve("${modpackId}_${verName}")
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
            val file = ClientDirs.dlPacksDir.resolve("${modpackId}_$verName.zip")
            val hash = server.makeRequest<String>("modpack/$modpackId/version/$verName/client/hash").data
                ?: throw IllegalStateException("客户端包hash为空")
            if (file.exists() && file.sha1 == hash) {
                ctx.emitProgress(TaskProgress("客户端整合包已存在", 1f))
                clientPackFile = file
                return@Leaf
            }
            ctx.emitProgress(TaskProgress("开始下载...", 0f))
            server.download("modpack/$modpackId/version/$verName/client", file.absolutePath) { prog ->
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
                ctx.emitProgress(TaskProgress(msg, fraction))
            }
            if (hash != file.sha1) {
                file.delete()
                throw IllegalStateException("客户端包下载损坏，请重试")
            }
            clientPackFile = file
            ctx.emitProgress(TaskProgress("下载完成", 1f))
        }

        val prepareVersionDirTask = Task.Leaf("准备安装目录") { ctx ->
            if (versionDir.exists()) {
                ctx.emitProgress(TaskProgress("清理旧版本文件...", null))
                if (!versionDir.deleteRecursively()) {
                    throw IllegalStateException("无法清理旧版本目录: ${versionDir.absolutePath}")
                }
            }
            if (!versionDir.exists()) {
                versionDir.mkdirs()
            }
            ctx.emitProgress(TaskProgress("目录已就绪", 1f))
        }

        val extractTask = Task.Leaf("解压客户端整合包") { ctx ->
            val clientPack = clientPackFile ?: throw IllegalStateException("客户端包未准备好")
            val ioBufferSize = 64 * 1024
            val copyBuffer = ByteArray(ioBufferSize)
            ctx.emitProgress(TaskProgress("扫描压缩包内容...", 0f))
            clientPack.openChineseZip().use { zip ->
                val entries = zip.entries().asSequence().toList()
                val fileEntries = entries.filterNot { it.isDirectory }
                val knownTotalBytes = fileEntries.mapNotNull { entry ->
                    entry.size.takeIf { it > 0L }
                }.sum()
                var extractedFiles = 0
                var extractedBytes = 0L
                var lastEmitTs = 0L
                val createdDirs = hashSetOf(versionDir.absolutePath)

                fileEntries.forEachIndexed { index, entry ->
                    val relativePath = entry.name.trimStart('/')
                    if (relativePath.isBlank()) return@forEachIndexed
                    val destination = versionDir.resolve(relativePath)
                    destination.parentFile?.let { parent ->
                        val parentPath = parent.absolutePath
                        if (parentPath !in createdDirs) {
                            parent.mkdirs()
                            createdDirs += parentPath
                        }
                    }
                    zip.getInputStream(entry).use { input ->
                        destination.outputStream().buffered(ioBufferSize).use { output ->
                            while (true) {
                                val read = input.read(copyBuffer)
                                if (read <= 0) break
                                output.write(copyBuffer, 0, read)
                                extractedBytes += read
                            }
                        }
                    }
                    extractedFiles += 1
                    val now = System.currentTimeMillis()
                    val shouldEmit = now - lastEmitTs >= 120L || index == fileEntries.lastIndex
                    if (shouldEmit) {
                        val fraction = if (knownTotalBytes > 0L) {
                            (extractedBytes.toFloat() / knownTotalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            extractedFiles.toFloat() / fileEntries.size.coerceAtLeast(1)
                        }
                        ctx.emitProgress(TaskProgress("解压中 $extractedFiles/${fileEntries.size}", fraction))
                        lastEmitTs = now
                    }
                }
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
            """
            lang:zh_cn
            darkMojangStudiosBackground:true
            forceUnicodeFont:true
            """.trimIndent().let { versionDir.resolve("options.txt").writeText(it) }
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

    fun isVersionInstalled(modpackId: ObjectId, verName: String): Boolean {
        val versionDir = getVersionDir(modpackId, verName)
        if (!versionDir.exists()) return false
        versionDir.resolve("mods").takeIf { it.exists() } ?: return false
        return true
    }

    suspend fun fetchSourceIntro(url: String) : Result<String>{
        return ok("")
    }
}

// ---- Types and functions moved from desktopMain for cross-platform use ----

sealed class StartPlayResult {
    data class Ready(val args: McPlayArgs) : StartPlayResult()
    data class NeedMc(val ver: McVersion) : StartPlayResult()
    data class NeedInstall(val task: Task) : StartPlayResult()
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
    val status = statusResp.data ?: throw RequestError("获取地图状态失败: ${statusResp.msg}")

    val versionResp = server.makeRequest<Modpack.Version>("modpack/${modpack.id}/version/$packVer")
    val version = versionResp.data ?: throw RequestError("获取整合包版本信息失败: ${versionResp.msg}")

    if (!(modpack.mcVer.firstLoaderDir.exists())) {
        return StartPlayResult.NeedMc(modpack.mcVer)
    }
    if (!ModpackService.isVersionInstalled(modpack.id, packVer)) {
        val task = with(ModpackService) { version.startInstall(modpack.mcVer, modpack.modloader, modpack.name) }
        return StartPlayResult.NeedInstall(task)
    }

    when (status) {
        HostStatus.PLAYABLE, HostStatus.STARTED -> Unit
        HostStatus.STOPPED -> {
            val startResp = server.makeRequest<Unit>("host/${_id}/start", HttpMethod.Post)
            if (!startResp.ok) {
                throw RequestError("启动地图失败: ${startResp.msg}")
            }
        }
        else -> throw RequestError("地图状态未知，无法游玩")
    }

    if (GameService.started) {
        throw RequestError("mc运行中，如需切换要玩的地图，请先关闭mc")
    }
    if(!isDesktop){
        val verDir = ModpackService.getVersionDir(version.modpackId,version.name)
        ModpackService.installRdiCore(modpack.mcVer,modpack.modloader,verDir)
    }
    val bgp = CONF.carrier != 0
    val versionId = "${modpack.id.str}_${version.name}"
    val playArg = "${server.hqUrl}\n" +
            "${if (bgp) server.bgpIp else server.ip}:${server.gamePort}\n"+
            "${name}\n"+
            "$port\n"+
            "${loggedAccount.uuid}\n"+
            loggedAccount.name

    return StartPlayResult.Ready(
        McPlayArgs(
            title = "游玩 $name",
            mcVer = modpack.mcVer,
            versionId = versionId,
            playArg = playArg
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
