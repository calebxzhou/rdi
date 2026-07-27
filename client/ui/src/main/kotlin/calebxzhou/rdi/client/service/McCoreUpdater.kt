package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.net.downloadFileFrom
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class McCoreUpdateResult(
    val checkedCount: Int,
    val updatedCount: Int
)

object McCoreUpdater {
    suspend fun update(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit
    ): Result<McCoreUpdateResult> = runCatching {
        val targets = McVersion.entries.filter { it.enabled }.flatMap { mcVersion ->
            mcVersion.loaderVersions.keys.map { loader ->
                val slug = "${mcVersion.mcVer}-${loader.name.lowercase()}"
                slug to DL_MOD_DIR.resolve("rdi-5-mc-client-$slug.jar").absoluteFile
            }
        }
        var updatedCount = 0
        targets.forEach { (slug, targetFile) ->
            val expectedSha1 = server.makeRequest<String>("update/mc/$slug/hash").data
                ?: throw RequestError("获取MC核心版本信息失败: $slug")
            if (targetFile.exists() && targetFile.sha1.equals(expectedSha1, true)) return@forEach

            onStatus("准备下载${targetFile.name}...")
            downloadAndReplace(
                targetFile = targetFile,
                downloadUrl = "${server.hqUrl}/update/mc/$slug",
                expectedSha1 = expectedSha1,
                label = targetFile.name,
                onDetail = onDetail
            ).getOrThrow()
            updatedCount++
            onStatus("${targetFile.name}更新完成")
        }
        McCoreUpdateResult(targets.size, updatedCount)
    }

    private suspend fun downloadAndReplace(
        targetFile: File,
        downloadUrl: String,
        expectedSha1: String,
        label: String,
        onDetail: (String) -> Unit
    ): Result<Unit> {
        val parentDir = targetFile.absoluteFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()
        val tempFile = File(parentDir, "${targetFile.name}.downloading.${System.currentTimeMillis()}")

        return runCatching {
            tempFile.toPath().downloadFileFrom(
                url = downloadUrl,
                validator = { path ->
                    if (path.toFile().sha1.equals(expectedSha1, true)) Result.success(Unit)
                    else Result.failure(IllegalStateException("文件损坏了，请重下"))
                }
            ) { onDetail(it.detailText(label)) }.getOrThrow()
            replaceDownloadedFile(tempFile, targetFile, onDetail).getOrThrow()
            onDetail("核心文件已更新至最新版本")
        }.onFailure {
            tempFile.delete()
            onDetail(it.message ?: "下载失败，请检查网络后重试")
        }
    }
}

private fun replaceDownloadedFile(
    tempFile: File,
    targetFile: File,
    onDetail: (String) -> Unit
): Result<Unit> {
    val parentDir = targetFile.absoluteFile.parentFile ?: File(".")

    fun deleteWithRetry(file: File): Boolean {
        repeat(5) {
            if (!file.exists() || file.delete()) return true
            Thread.sleep(200)
        }
        return !file.exists()
    }

    fun overwriteLockedFile(source: File, target: File): Boolean = runCatching {
        FileInputStream(source).channel.use { input ->
            FileOutputStream(target, false).channel.use { output ->
                output.truncate(0)
                var position = 0L
                while (position < input.size()) {
                    val transferred = input.transferTo(position, 1024 * 1024, output)
                    if (transferred <= 0) break
                    position += transferred
                }
            }
        }
        true
    }.getOrElse { false }

    val backupFile = targetFile.takeIf(File::exists)
        ?.let { File(parentDir, "${targetFile.name}.backup.${System.currentTimeMillis()}") }
    return runCatching {
        backupFile?.let { targetFile.copyTo(it, overwrite = true) }
        if (targetFile.exists() && !deleteWithRetry(targetFile)) {
            targetFile.deleteOnExit()
            if (!overwriteLockedFile(tempFile, targetFile)) {
                throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
            }
            tempFile.delete()
            return@runCatching
        }
        Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }.recoverCatching {
        if (targetFile.exists() && !deleteWithRetry(targetFile)) {
            targetFile.deleteOnExit()
            if (!overwriteLockedFile(tempFile, targetFile)) {
                throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
            }
            tempFile.delete()
        } else {
            Files.copy(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            tempFile.delete()
        }
        Unit
    }.onSuccess {
        backupFile?.delete()
    }.onFailure {
        onDetail("替换核心文件失败")
    }
}
