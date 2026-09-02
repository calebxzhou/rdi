package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2
import calebxzhou.rdi.common.model.Host2SetupStatus
import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.serdesJson
import calebxzhou.rdi.common.service.ModService
import calebxzhou.rdi.common.util.objectId
import calebxzhou.rdi.master.HOST2_DIR
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzhou.rdi.master.service.MailService
import com.github.luben.zstd.ZstdInputStream
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

class Host2SetupService(
    private val database: DatabaseProvider,
    private val repository: Host2Repository,
    private val hostService: Host2Service,
    private val modDownloadService: Host2ModDownloadService
) {
    private val lgr by Loggers
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<UUID, Job>()

    suspend fun receive(player: RAccount, id: UUID, call: ApplicationCall) {
        val host = hostService.requireOwner(player, id)
        if (host.setupStatus !in UPLOADABLE_STATUSES) throw RequestError("当前配置状态不能上传server pack")

        val stagingDir = newStagingDir(id)
        val archive = stagingDir.resolve("upload.tar.zst")
        var metadata: Host2.ServerPackUploadDto? = null
        var receivedFile = false
        try {
            val multipart = call.receiveMultipart(formFieldLimit = MAX_ARCHIVE_SIZE)
            while (true) {
                val part = multipart.readPart() ?: break
                try {
                    when (part) {
                        is PartData.FormItem -> if (part.name == "metadata") {
                            metadata = runCatching {
                                serdesJson.decodeFromString<Host2.ServerPackUploadDto>(part.value)
                            }.getOrElse { throw RequestError("server pack metadata无效") }
                        }

                        is PartData.FileItem -> if (part.name == "file") {
                            if (receivedFile) throw RequestError("只能上传一个server pack")
                            receiveArchive(part.provider(), archive)
                            receivedFile = true
                        }

                        else -> Unit
                    }
                } finally {
                    part.dispose()
                }
            }
            val dto = metadata ?: throw RequestError("缺少server pack metadata")
            if (!receivedFile) throw RequestError("缺少server pack文件")
            validateManifest(dto.mods)
            database.transaction {
                val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                if (locked.ownerId != host.ownerId || locked.setupStatus !in UPLOADABLE_STATUSES) {
                    throw RequestError("新版房间状态已改变")
                }
                repository.updateSetupStatus(id, Host2SetupStatus.PROCESSING)
            }
            jobs[id] = scope.launch {
                process(id, host, dto, stagingDir, archive)
            }.also { job -> job.invokeOnCompletion { jobs.remove(id, job) } }
        } catch (error: Throwable) {
            stagingDir.takeIf(File::exists)?.deleteRecursivelyNoSymlink()
            throw error
        }
    }

    suspend fun cancel(id: UUID) {
        jobs.remove(id)?.cancelAndJoin()
    }

    suspend fun deleteServerPack(player: RAccount, id: UUID) = hostService.withMutation(id) {
        val host = hostService.requireOwner(player, id)
        if (host.setupStatus == Host2SetupStatus.PROCESSING) throw RequestError("server pack正在处理")
        if (Host2RuntimeService.status(id) != HostStatus.STOPPED) throw RequestError("请先停止新版房间")
        val hostDir = HOST2_DIR.resolve(id.toString())
        val deletingDir = HOST2_DIR.resolve(".deleting-pack").resolve("$id-${UUID.randomUUID()}")
        var moved = false
        try {
            database.transaction {
                val locked = repository.findByIdForUpdate(id) ?: throw RequestError("无此新版房间")
                if (locked.ownerId != host.ownerId) throw RequestError("无权限")
                if (hostDir.exists()) {
                    deletingDir.parentFile.mkdirs()
                    Files.move(hostDir.toPath(), deletingDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    moved = true
                }
                repository.updateSetupStatus(id, Host2SetupStatus.AWAITING_UPLOAD)
            }
        } catch (error: Throwable) {
            if (moved && deletingDir.exists() && !hostDir.exists()) Files.move(deletingDir.toPath(), hostDir.toPath())
            throw error
        }
        deletingDir.takeIf(File::exists)?.deleteRecursivelyNoSymlink()
    }

    suspend fun recover() {
        database.transaction { repository.listBySetupStatus(Host2SetupStatus.PROCESSING) }
            .forEach { host ->
                database.transaction { repository.updateSetupStatus(host.id, Host2SetupStatus.FAILED) }
                notifyOwner(host, "新版房间配置已中断", "服务器重启中断了server pack处理，请重新上传。")
            }
        HOST2_DIR.resolve(".staging").listFiles()?.forEach { it.deleteRecursivelyNoSymlink() }
        recoverDeletingDirs()
        recoverDeletingPackDirs()
    }

    fun close() {
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun process(
        id: UUID,
        host: Host2Record,
        dto: Host2.ServerPackUploadDto,
        stagingDir: File,
        archive: File
    ) {
        val contentDir = stagingDir.resolve("content")
        try {
            extractTarZst(archive, contentDir)
            validateServerRoot(contentDir)
            val archiveModIds = readModIds(contentDir.resolve("mods"))
            validateReservedMods(host, archiveModIds)
            downloadMatchedMods(dto.mods, host, contentDir.resolve("mods"), archiveModIds)
            contentDir.resolve("mods").mkdirs()
            contentDir.resolve("config").mkdirs()
            contentDir.resolve("logs").mkdirs()
            contentDir.resolve("eula.txt").writeText("eula=true\n")
            val finalSize = contentDir.walkTopDown().filter(File::isFile).sumOf(File::length)
            if (finalSize > MAX_EXTRACTED_SIZE) throw RequestError("server pack处理后超过8GiB")
            install(id, contentDir)
            database.transaction { repository.updateSetupStatus(id, Host2SetupStatus.READY) }
            notifyOwner(host, "新版房间配置完成", "${host.name}的server pack已处理完成。")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            lgr.error(error) { "Host2 $id server pack处理失败" }
            database.transaction {
                repository.findById(id)?.let { repository.updateSetupStatus(id, Host2SetupStatus.FAILED) }
            }
            notifyOwner(host, "新版房间配置失败", "${host.name}的server pack处理失败，请检查文件后重新上传。")
        } finally {
            stagingDir.takeIf(File::exists)?.deleteRecursivelyNoSymlink()
        }
    }

    private suspend fun downloadMatchedMods(
        mods: List<Mod>,
        host: Host2Record,
        modsDir: File,
        archiveModIds: Set<String>
    ) {
        val selectedProjects = mutableSetOf<Pair<String, String>>()
        val selectedModIds = mutableSetOf<String>()
        mods.forEach { mod ->
            val key = mod.platform.lowercase() to mod.projectId
            if (!selectedProjects.add(key)) throw RequestError("server pack包含重复Mod项目: ${mod.slug}")
            val downloaded = modDownloadService.download(mod, host.mcVersion, host.modLoader, modsDir)
            val downloadedIds = readModIds(listOf(downloaded))
            validateReservedMods(host, downloadedIds)
            if (downloadedIds.any { it in archiveModIds }) {
                downloaded.delete()
                throw RequestError("${mod.slug}与server pack内Mod重复")
            }
            if (downloadedIds.any { !selectedModIds.add(it) }) {
                downloaded.delete()
                throw RequestError("${mod.slug}与已选择Mod重复")
            }
        }
    }

    private fun validateManifest(mods: List<Mod>) {
        mods.forEach { mod ->
            if (mod.platform.lowercase() !in SETUP_MOD_PLATFORMS) {
                throw RequestError("server pack自动下载仅支持CurseForge和Modrinth")
            }
            if (mod.side == Mod.Side.CLIENT) throw RequestError("server pack不能包含客户端专用Mod: ${mod.slug}")
        }
    }

    private fun validateReservedMods(host: Host2Record, modIds: Set<String>) {
        val reserved = buildSet {
            add("rdi")
            if (host.mcVersion.mcVer in KOTLIN_FOR_FORGE_VERSIONS) add("kotlinforforge")
        }
        val conflict = modIds.firstOrNull { it in reserved }
        if (conflict != null) throw RequestError("server pack不能包含平台保留Mod: $conflict")
    }

    private fun validateServerRoot(root: File) {
        val rootNames = root.listFiles()?.mapTo(mutableSetOf()) { it.name.lowercase() }.orEmpty()
        if (SERVER_ROOT_MARKERS.none { it in rootNames }) {
            throw RequestError("选择的目录不是有效server根目录")
        }
    }

    private fun readModIds(modsDir: File): Set<String> =
        readModIds(modsDir.listFiles { file -> file.isFile && file.extension.equals("jar", true) }?.toList().orEmpty())

    private fun readModIds(files: List<File>): Set<String> = files.flatMapTo(mutableSetOf()) { file ->
        runCatching {
            JarFile(file).use { jar -> ModService.run { jar.readModMeta()?.modIds.orEmpty() } }
        }.getOrElse { throw RequestError("无法读取Mod文件: ${file.name}") }
    }

    private fun extractTarZst(archive: File, targetDir: File) {
        targetDir.mkdirs()
        val targetPath = targetDir.toPath().toAbsolutePath().normalize()
        var entryCount = 0
        var extractedBytes = 0L
        TarArchiveInputStream(ZstdInputStream(archive.inputStream().buffered())).use { input ->
            while (true) {
                val entry = input.nextTarEntry ?: break
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) throw RequestError("server pack文件数量超过${MAX_ARCHIVE_ENTRIES}")
                validateEntryType(entry)
                val name = entry.name.replace('\\', '/')
                if (name.startsWith('/') || WINDOWS_ABSOLUTE_PATH.matches(name)) throw RequestError("server pack包含绝对路径")
                val outputPath = targetPath.resolve(name).normalize()
                if (!outputPath.startsWith(targetPath)) throw RequestError("server pack包含越界路径")
                if (shouldSkipServerPackEntry(name, entry.isDirectory)) continue
                if (entry.isDirectory) {
                    Files.createDirectories(outputPath)
                    continue
                }
                if (entry.size < 0) throw RequestError("server pack包含未知大小文件")
                extractedBytes += entry.size
                if (extractedBytes > MAX_EXTRACTED_SIZE) throw RequestError("server pack解压后超过8GiB")
                outputPath.parent?.let { Files.createDirectories(it) }
                Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW).use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }

    private fun validateEntryType(entry: TarArchiveEntry) {
        if (!entry.isDirectory && !entry.isFile) throw RequestError("server pack包含不支持的文件类型")
        if (entry.isSymbolicLink || entry.isLink) throw RequestError("server pack不能包含链接")
    }

    private fun install(id: UUID, contentDir: File) {
        val target = HOST2_DIR.resolve(id.toString())
        if (target.exists()) target.deleteRecursivelyNoSymlink()
        Files.move(contentDir.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private suspend fun notifyOwner(host: Host2Record, title: String, content: String) {
        runCatching { MailService.sendSystemMail(host.ownerId.objectId, title, content) }
            .onFailure { lgr.error(it) { "Host2 ${host.id}发送系统邮件失败" } }
    }

    private suspend fun recoverDeletingDirs() {
        HOST2_DIR.resolve(".deleting").listFiles()?.filter(File::isDirectory)?.forEach { deletingDir ->
            val id = runCatching { UUID.fromString(deletingDir.name) }.getOrNull() ?: return@forEach
            val exists = database.transaction { repository.findById(id) != null }
            if (exists) {
                val target = HOST2_DIR.resolve(deletingDir.name)
                if (!target.exists()) Files.move(deletingDir.toPath(), target.toPath())
            } else {
                deletingDir.deleteRecursivelyNoSymlink()
            }
        }
    }

    private suspend fun recoverDeletingPackDirs() {
        HOST2_DIR.resolve(".deleting-pack").listFiles()?.filter(File::isDirectory)?.forEach { deletingDir ->
            val id = deletingDir.name.take(36).let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@forEach
            val host = database.transaction { repository.findById(id) }
            val target = HOST2_DIR.resolve(id.toString())
            if (host != null && host.setupStatus != Host2SetupStatus.AWAITING_UPLOAD && !target.exists()) {
                Files.move(deletingDir.toPath(), target.toPath())
            } else {
                deletingDir.deleteRecursivelyNoSymlink()
            }
        }
    }

    private fun newStagingDir(id: UUID): File =
        HOST2_DIR.resolve(".staging").resolve("$id-${UUID.randomUUID()}").apply { mkdirs() }

    private suspend fun receiveArchive(channel: ByteReadChannel, target: File) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                total += read
                if (total > MAX_ARCHIVE_SIZE) throw RequestError("server pack最大允许4GiB")
                output.write(buffer, 0, read)
            }
        }
    }

}

private fun shouldSkipServerPackEntry(path: String, directory: Boolean): Boolean {
    val normalized = path.trimEnd('/').lowercase()
    val segments = normalized.split('/')
    val name = segments.lastOrNull().orEmpty()
    val extension = name.substringAfterLast('.', "")
    if (segments.any { it in SKIPPED_SERVER_PACK_DIRS }) return true
    if (directory) return false
    if (normalized == "world/session.lock") return true
    if (name in setOf(".ds_store", "desktop.ini")) return true
    if (extension == "db" || extension in SERVER_PACK_MEDIA_EXTENSIONS) return true
    if (!normalized.startsWith("mods/") && extension in setOf("jar", "exe") && name != LWJGL3IFY_LAUNCHER) return true
    return !normalized.startsWith("mods/") && extension.isBlank()
}

private const val MAX_ARCHIVE_SIZE = 4L * 1024 * 1024 * 1024
private const val MAX_EXTRACTED_SIZE = 8L * 1024 * 1024 * 1024
private const val MAX_ARCHIVE_ENTRIES = 100_000
private val UPLOADABLE_STATUSES = setOf(Host2SetupStatus.AWAITING_UPLOAD, Host2SetupStatus.FAILED)
private val SETUP_MOD_PLATFORMS = setOf("cf", "mr")
private val SERVER_ROOT_MARKERS = setOf("mods", "config", "server.properties", "world")
private val KOTLIN_FOR_FORGE_VERSIONS = setOf("1.20.1", "1.21.1")
private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/.*")
private val SKIPPED_SERVER_PACK_DIRS = setOf("libraries", "cache", "logs", "crash-reports", "media")
private val SERVER_PACK_MEDIA_EXTENSIONS = setOf("psd", "png", "jpg", "jpeg", "webp", "mp4", "ogg", "wav")
private const val LWJGL3IFY_LAUNCHER = "lwjgl3ify-forgepatches.jar"
