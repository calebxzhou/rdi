package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.ui.pickAwtOpenFiles
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.service.TaczGunpackValidator
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import java.io.File

internal const val TACZ_ROOT_DIR = "tacz"
internal const val TACZ_FILE_MAX_BYTES: Long = 100L * 1024 * 1024
internal const val TACZ_MAX_ZIP_FILES = 10

fun selectHostTaczFiles(): List<File>? {
    return pickAwtOpenFiles(
        title = "选择TaCZ枪包ZIP",
        filenameFilter = { _, name -> name.endsWith(".zip", ignoreCase = true) }
    )
        ?.filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
        ?.distinctBy { it.absolutePath }
        ?.takeIf { it.isNotEmpty() }
}

fun hostTaczChildPath(fileName: String): String {
    val normalizedName = fileName.replace('\\', '/').substringAfterLast('/').trim()
    require(normalizedName.isNotBlank()) { "文件名不能为空" }
    return "$TACZ_ROOT_DIR/$normalizedName"
}

fun createHostTaczUploadTask(
    hostId: String,
    files: List<File>,
    onUploaded: suspend () -> Unit
): Task2 {
    val uploadTasks = files.map { file ->
        Task2.Leaf("上传${file.name}") { ctx ->
            ctx.emit(Task2Progress("校验${file.name}", 0f))
            withContext(Dispatchers.IO) {
                TaczGunpackValidator.validate(file).getOrThrow()
            }
            ctx.ensureActive()
            ctx.emit(Task2Progress("上传${file.name}", 0.4f))
            uploadHostTaczFile(
                hostId = hostId,
                targetPath = hostTaczChildPath(file.name),
                file = file
            )
            ctx.emit(Task2Progress("完成${file.name}", 1f))
        }
    }
    return Task2.Sequence(
        title = "上传TaCZ枪包${files.size}个",
        children = uploadTasks + Task2.Leaf("刷新TaCZ枪包列表") { ctx ->
            ctx.emit(Task2Progress("刷新TaCZ枪包列表", 0f))
            onUploaded()
            ctx.emit(Task2Progress("刷新完成", 1f))
        }
    )
}

private suspend fun uploadHostTaczFile(
    hostId: String,
    targetPath: String,
    file: File
): Host.FileUploadVo {
    val multipartContent = MultiPartFormDataContent(
        formData {
            append("path", targetPath)
            append(
                key = "file",
                value = InputProvider { file.inputStream().asInput().buffered() },
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name.replace("\"", "")}\"")
                }
            )
        }
    )
    val response = server.makeRequest<Host.FileUploadVo>(
        path = "host/$hostId/files/file",
        method = HttpMethod.Post,
        params = mapOf("path" to targetPath)
    ) {
        setBody(multipartContent)
    }
    if (!response.ok) throw RequestError(response.msg)
    return response.data ?: throw RequestError("上传TaCZ枪包失败")
}
