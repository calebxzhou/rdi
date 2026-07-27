package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.net.LocalArtifactHashAlgorithm
import calebxzhou.rdi.common.net.LocalArtifactRequest
import calebxzhou.rdi.common.net.LocalArtifactReuse
import calebxzhou.rdi.common.net.downloadFileFrom
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

        if (expectedSha1 != null && targetDirName.equals("mods", ignoreCase = true)) {
            val localSource = LocalArtifactReuse.reuse(
                LocalArtifactRequest(
                    algorithm = LocalArtifactHashAlgorithm.SHA1,
                    hash = expectedSha1,
                    size = file.size?.takeIf { it > 0 }
                ),
                target
            ).onFailure {
                lgr.warn(it) { "查找本地Mod失败，将使用网络下载: $filename" }
            }.getOrNull()
            if (localSource != null) {
                ctx.emit(Task2Progress("已从本地实例复用${filename}", 1f))
                return@Leaf
            }
        }

        ctx.emit(Task2Progress("开始下载${filename}", 0f))
        target.downloadFileFrom(
            url = file.url,
            knownSize = file.size ?: 0L,
            validator = { path ->
                if (expectedSha1 == null) {
                    Result.success(Unit)
                } else {
                    val actualSha1 = path.sha1.lowercase()
                    if (actualSha1 == expectedSha1) {
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("${projectDisplayName}SHA1校验失败: $filename"))
                    }
                }
            }
        ) { progress ->
            val message = if (progress.totalBytes > 0) {
                "下载${filename} ${progress.bytesDownloaded.toFileSizeText()}/${progress.totalBytes.toFileSizeText()}"
            } else {
                "下载${filename} ${progress.bytesDownloaded.toFileSizeText()}"
            }
            ctx.emit(Task2Progress(message, progress.fraction.takeIf { it >= 0f }))
        }.getOrThrow()

        ctx.emit(Task2Progress("已下载到${packdir.vo.name}的${targetDirName}目录", 1f))
    }

    private val lgr by Loggers
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
