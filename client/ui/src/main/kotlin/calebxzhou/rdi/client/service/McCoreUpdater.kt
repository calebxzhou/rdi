package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.ModLoader
import calebxzhou.rdi.common.net.downloadFileFrom
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class McCoreUpdateResult(val updated: Boolean)

object McCoreUpdater {
    fun slug(mcVersion: McVersion, modLoader: ModLoader) =
        "${mcVersion.mcVer}-${modLoader.name.lowercase()}"

    fun cacheFile(mcVersion: McVersion, modLoader: ModLoader) =
        ClientDirs.dlModsDir.resolve("rdi-5-mc-client-${slug(mcVersion, modLoader)}.jar").absoluteFile

    suspend fun update(
        mcVersion: McVersion,
        modLoader: ModLoader,
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit
    ): Result<McCoreUpdateResult> = runCatching {
        val slug = slug(mcVersion, modLoader)
        val targetFile = cacheFile(mcVersion, modLoader)
        onStatus("检查RDI核心版本...")
        val expectedSha1 = server.makeRequest<String>("update/mc/$slug/hash").data
            ?.trim()
            ?.takeIf { it.matches(Regex("^[0-9a-fA-F]{40}$")) }
            ?: throw RequestError("获取MC核心版本信息失败: $slug")
        if (targetFile.exists() && targetFile.sha1.equals(expectedSha1, true)) {
            return@runCatching McCoreUpdateResult(updated = false)
        }

        onStatus("准备下载${targetFile.name}...")
        downloadAndReplace(
            targetFile = targetFile,
            downloadUrl = "${server.hqUrl}/update/mc/$slug",
            expectedSha1 = expectedSha1,
            onDetail = onDetail
        ).getOrThrow()
        onStatus("${targetFile.name}更新完成")
        McCoreUpdateResult(updated = true)
    }

    private suspend fun downloadAndReplace(
        targetFile: File,
        downloadUrl: String,
        expectedSha1: String,
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
            ) { onDetail(it.detailText(targetFile.name)) }.getOrThrow()
            replaceCoreContents(tempFile, targetFile, expectedSha1).getOrThrow()
            onDetail("核心文件已更新至最新版本")
        }.onFailure {
            tempFile.delete()
            onDetail(it.message ?: "下载失败，请检查网络后重试")
        }
    }

    internal fun replaceCoreContents(
        downloadedFile: File,
        targetFile: File,
        expectedSha1: String
    ): Result<Unit> {
        if (!targetFile.exists()) {
            return runCatching {
                Files.move(downloadedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                check(targetFile.sha1.equals(expectedSha1, true)) { "核心文件替换后校验失败" }
            }.onFailure {
                targetFile.delete()
            }
        }

        val backupFile = targetFile.parentFile.resolve("${targetFile.name}.replacing-backup")
        return runCatching {
            targetFile.copyTo(backupFile, overwrite = true)
            overwriteFileContents(downloadedFile, targetFile)
            check(targetFile.sha1.equals(expectedSha1, true)) { "核心文件替换后校验失败" }
            downloadedFile.delete()
            Unit
        }.recoverCatching { replaceError ->
            runCatching { overwriteFileContents(backupFile, targetFile) }
                .onFailure(replaceError::addSuppressed)
            backupFile.delete()
            throw replaceError
        }.onSuccess {
            backupFile.delete()
        }
    }

    private fun overwriteFileContents(source: File, target: File) {
        FileInputStream(source).channel.use { input ->
            FileOutputStream(target, false).channel.use { output ->
                output.truncate(0)
                var position = 0L
                while (position < input.size()) {
                    val transferred = input.transferTo(position, 1024 * 1024, output)
                    check(transferred > 0) { "核心文件写入不完整" }
                    position += transferred
                }
                output.force(true)
            }
        }
    }
}
