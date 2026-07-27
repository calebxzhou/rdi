package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.*
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import java.io.File
import java.nio.file.Files
import java.util.*

fun buildHost2ServerPackUploadTask(hostId: UUID, sourceDir: File): Task2 = Task2.Leaf("上传新版房间server pack") { ctx ->
    var archive: File? = null
    try {
        ctx.emit(Task2Progress("识别CurseForge/Modrinth Mod", 0f))
        val prepared = withContext(Dispatchers.IO) { prepareHost2Pack(sourceDir) { ctx.emit(it) } }
        ctx.ensureActive()
        ctx.emit(Task2Progress("压缩server pack", 0.35f))
        archive = withContext(Dispatchers.IO) { buildHost2Archive(sourceDir, prepared.files) }
        if (archive!!.length() > HOST2_ARCHIVE_LIMIT) throw RequestError("server pack压缩后超过4GiB")
        ctx.ensureActive()
        uploadHost2Pack(hostId, archive!!, prepared.mods) { sent, total ->
            val fraction = if (total <= 0) null else 0.45f + sent.toFloat() / total.toFloat() * 0.55f
            ctx.emit(Task2Progress("上传server pack", fraction))
        }
        ctx.emit(Task2Progress("服务器正在处理server pack", 1f))
    } finally {
        archive?.delete()
    }
}

private data class PreparedHost2Pack(val mods: List<Mod>, val files: List<ServerExtraFile>)

private suspend fun prepareHost2Pack(sourceDir: File, emit: (Task2Progress) -> Unit): PreparedHost2Pack {
    if (!sourceDir.isDirectory) throw RequestError("请选择解压后的server根目录")
    val rootNames = sourceDir.listFiles()?.mapTo(mutableSetOf()) { it.name.lowercase() }.orEmpty()
    if (HOST2_ROOT_MARKERS.none { it in rootNames }) throw RequestError("选择的目录不是server根目录")
    val modFiles = sourceDir.resolve("mods").listFiles { file -> file.isFile && file.extension.equals("jar", true) }.orEmpty()
    if (modFiles.isEmpty()) return PreparedHost2Pack(emptyList(), collectHost2Files(sourceDir, emptySet()))
    val loaded = loadServerPackMods(sourceDir, emptyList()) { progress -> emit(progress.toTask2Progress()) }.getOrThrow()
    val serverMods = loaded.mods.filter { it.side != Mod.Side.CLIENT }
    return PreparedHost2Pack(
        serverMods,
        loaded.serverExtraFiles.filterNot {
            Files.isSymbolicLink(it.sourceFile.toPath()) || it.relativePath.isHost2SkippedFile()
        }
    )
}

private fun collectHost2Files(root: File, excluded: Set<File>): List<ServerExtraFile> =
    root.walkTopDown()
        .onEnter { dir -> dir == root || !Files.isSymbolicLink(dir.toPath()) && !dir.relativeTo(root).path.isHost2SkippedDir() }
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && it !in excluded }
        .mapNotNull { file ->
            val path = file.relativeTo(root).path.replace('\\', '/')
            if (path.isHost2SkippedFile()) null else ServerExtraFile(file, path)
        }
        .toList()

private fun buildHost2Archive(root: File, files: List<ServerExtraFile>): File {
    ClientDirs.packProcDir.mkdirs()
    val target = ClientDirs.packProcDir.resolve("host2-${System.currentTimeMillis()}.tar.zst")
    val addedDirectories = mutableSetOf<String>()
    TarZstArchiveWriter(target).use { output ->
        HOST2_ROOT_MARKERS.map(root::resolve).filter(File::isDirectory).forEach { directory ->
            output.addDirectory(directory.name, directory.lastModified())
            addedDirectories += directory.name
        }
        files.forEach { entry ->
            val path = entry.relativePath.replace('\\', '/').trimStart('/')
            var parent = path.substringBeforeLast('/', "")
            val missing = mutableListOf<String>()
            while (parent.isNotBlank() && parent !in addedDirectories) {
                missing += parent
                parent = parent.substringBeforeLast('/', "")
            }
            missing.asReversed().forEach {
                output.addDirectory(it)
                addedDirectories += it
            }
            output.addFile(path, entry.sourceFile)
        }
    }
    return target
}

private suspend fun uploadHost2Pack(
    hostId: UUID,
    archive: File,
    mods: List<Mod>,
    onProgress: (Long, Long) -> Unit
) {
    val metadata = serdesJson.encodeToString(Host2.ServerPackUploadDto(mods))
    val content = MultiPartFormDataContent(formData {
        append("metadata", metadata, Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) })
        append(
            "file",
            InputProvider { archive.inputStream().asInput().buffered() },
            Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                append(HttpHeaders.ContentDisposition, "filename=\"server-pack.tar.zst\"")
            }
        )
    })
    val response = server.makeRequest<Unit>("host2/$hostId/server-pack", HttpMethod.Put) {
        timeout {
            requestTimeoutMillis = 2 * 60 * 60 * 1000L
            socketTimeoutMillis = 2 * 60 * 60 * 1000L
        }
        setBody(content)
        onUpload { sent, total -> onProgress(sent, total ?: archive.length()) }
    }
    if (!response.ok) throw RequestError(response.msg)
}

private fun String.isHost2SkippedDir(): Boolean {
    val path = replace('\\', '/').lowercase()
    val name = substringAfterLast('/').lowercase()
    return name in setOf("cache", "logs", "crash-reports") || path.startsWith("libraries/")
}

private fun String.isHost2SkippedFile(): Boolean {
    val path = replace('\\', '/').lowercase()
    val name = substringAfterLast('/')
    val extension = name.substringAfterLast('.', "")
    if (path == "world/session.lock") return true
    if (path.isHost2SkippedDir()) return true
    if (name in setOf(".ds_store", "desktop.ini")) return true
    if (extension == "db" || extension in HOST2_MEDIA_EXTENSIONS) return true
    if (!path.startsWith("mods/") && extension in setOf("jar", "exe") && name != LWJGL3IFY_LAUNCHER) return true
    if (!path.startsWith("mods/") && extension.isBlank()) return true
    return false
}

private const val HOST2_ARCHIVE_LIMIT = 4L * 1024 * 1024 * 1024
private val HOST2_ROOT_MARKERS = setOf("mods", "config", "server.properties", "world")
private val HOST2_MEDIA_EXTENSIONS = setOf("psd", "png", "jpg", "jpeg", "webp", "mp4", "ogg", "wav")
private const val LWJGL3IFY_LAUNCHER = "lwjgl3ify-forgepatches.jar"
