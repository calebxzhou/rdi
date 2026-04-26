package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.net.downloadFileFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

object ModrinthProjectDownloadService {
    fun downloadProjectFileTask2(
        file: ModrinthProjectVersionFileVo,
        packdir: ModpackLocalDir,
        projectDisplayName: String,
        targetDirName: String
    ): Task2 = Task2.Leaf("下载${projectDisplayName} ${file.filename}") { ctx ->
        val filename = file.safeFilename()
        val target = packdir.dir.resolve(targetDirName).resolve(filename).toPath()
        val expectedSha1 = file.sha1?.trim()?.lowercase()?.takeIf(String::isNotBlank)

        if (expectedSha1 != null && Files.isRegularFile(target) && target.sha1.equals(expectedSha1, ignoreCase = true)) {
            ctx.emit(Task2Progress("${projectDisplayName}已存在: $filename", 1f))
            return@Leaf
        }

        ctx.emit(Task2Progress("开始下载${filename}", 0f))
        val downloadedPath = target.downloadFileFrom(
            url = file.url,
            knownSize = file.size ?: 0L
        ) { progress ->
            val message = if (progress.totalBytes > 0) {
                "下载${filename} ${progress.bytesDownloaded.toFileSizeText()}/${progress.totalBytes.toFileSizeText()}"
            } else {
                "下载${filename} ${progress.bytesDownloaded.toFileSizeText()}"
            }
            ctx.emit(Task2Progress(message, progress.fraction.takeIf { it >= 0f }))
        }.getOrThrow()

        expectedSha1?.let {
            val actualSha1 = withContext(Dispatchers.IO) { downloadedPath.sha1.lowercase() }
            require(actualSha1 == it) { "${projectDisplayName}SHA1校验失败: $filename" }
        }

        ctx.emit(Task2Progress("已下载到${packdir.vo.name}的${targetDirName}目录", 1f))
    }
}

private fun ModrinthProjectVersionFileVo.safeFilename(): String =
    filename.replace('\\', '/').substringAfterLast('/').ifBlank { "modrinth-project-file.zip" }

private fun Long.toFileSizeText(): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    val text = if (unitIndex == 0) value.toLong().toString() else {
        val rounded = kotlin.math.round(value * 10).toInt()
        val whole = rounded / 10
        val fraction = rounded % 10
        if (fraction == 0) whole.toString() else "$whole.$fraction"
    }
    return "$text${units[unitIndex]}"
}
