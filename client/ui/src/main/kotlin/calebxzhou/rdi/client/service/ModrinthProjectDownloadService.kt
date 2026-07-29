package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.model.ModrinthProjectVersionFileVo
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.client.ui.McPlayStore
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ModrinthProjectDownloadService {
    fun downloadProjectFileTask2(
        file: ModrinthProjectVersionFileVo,
        packdir: ModpackLocalDir,
        projectId: String,
        versionId: String,
        projectDisplayName: String,
        targetDirName: String
    ): Task2 = Task2.Leaf("下载${projectDisplayName} ${file.filename}") { ctx ->
        require(packdir.dir.isDirectory) { "目标整合包已不存在" }
        val filename = file.safeFilename()
        val type = when (targetDirName) {
            "resourcepacks" -> LocalContentType.RESOURCE_PACK
            "shaderpacks" -> LocalContentType.SHADER_PACK
            else -> error("不支持的本地内容目录: $targetDirName")
        }
        val existing = LocalContentInstallStore.find(packdir, type, "modrinth", projectId).getOrThrow()
        val installingWhileRunning = existing == null && McPlayStore.aliveCount(packdir.versionId) > 0
        val targetDir = packdir.dir.resolve(targetDirName).also { it.mkdirs() }
        val target = targetDir.resolve(filename).toPath()
        val expectedSha1 = file.sha1?.trim()?.lowercase()?.takeIf(String::isNotBlank)
            ?: error("当前文件缺少SHA1，无法安全安装")

        if (existing?.versionId == versionId && Files.isRegularFile(target) &&
            target.sha1.equals(expectedSha1, ignoreCase = true)
        ) {
            ctx.emit(Task2Progress("${projectDisplayName}已存在: $filename", 1f))
            return@Leaf
        }
        if (existing != null && McPlayStore.aliveCount(packdir.versionId) > 0) {
            error("当前整合包正在运行，不能更新${projectDisplayName}")
        }
        if (Files.exists(target) && existing?.fileName != filename) {
            error("目标文件已存在且不属于RDI管理: $filename")
        }

        val workDir = packdir.dir.resolve(".rdi/installing").also { it.mkdirs() }
        val workKey = projectId.safePathSegment()
        val staged = workDir.resolve("$workKey-$filename.part").toPath()
        Files.deleteIfExists(staged)
        ctx.emit(Task2Progress("开始下载${filename}", 0f))
        staged.downloadFileFrom(
            url = file.url,
            knownSize = file.size ?: 0L,
            validator = { path ->
                val actualSha1 = path.sha1.lowercase()
                if (actualSha1 == expectedSha1) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("${projectDisplayName}SHA1校验失败: $filename"))
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

        val oldTarget = existing?.let { targetDir.resolve(it.fileName).toPath() }
        val backup = workDir.resolve("$workKey.backup").toPath()
        Files.deleteIfExists(backup)
        try {
            if (oldTarget != null && Files.exists(oldTarget)) {
                Files.move(oldTarget, backup, StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING)
            LocalContentInstallStore.replace(
                packdir,
                listOf(
                    LocalContentInstallRecord(
                        type = type,
                        source = "modrinth",
                        projectId = projectId,
                        versionId = versionId,
                        fileName = filename,
                        hash = expectedSha1
                    )
                )
            ).getOrThrow()
            runCatching { Files.deleteIfExists(backup) }
        } catch (cause: Exception) {
            Files.deleteIfExists(target)
            if (oldTarget != null && Files.exists(backup)) {
                Files.move(backup, oldTarget, StandardCopyOption.REPLACE_EXISTING)
            }
            throw cause
        } finally {
            Files.deleteIfExists(staged)
        }
        ctx.emit(
            Task2Progress(
                if (installingWhileRunning) "已安装，重启游戏后生效"
                else "已下载到${packdir.vo.name}的${targetDirName}目录",
                1f
            )
        )
    }
}

private fun ModrinthProjectVersionFileVo.safeFilename(): String =
    filename.replace('\\', '/').substringAfterLast('/').ifBlank { "modrinth-project-file.zip" }

private fun String.safePathSegment(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

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
