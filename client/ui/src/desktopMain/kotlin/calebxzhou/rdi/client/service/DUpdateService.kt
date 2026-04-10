package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.exception.RequestError
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val lgr by Loggers

internal actual suspend fun syncPlatformUpdates(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit
): PlatformUpdateSyncResult {
    val libDir = File("lib").absoluteFile
    if (!libDir.exists()) libDir.mkdirs()

    val serverEntries = server.makeRequest<Map<String, String>>("update/ui/libs").data
        ?: throw RequestError("获取UI库信息失败")
    var updated = false

    serverEntries.forEach { (name, sha) ->
        val localFile = File(libDir, name)
        val needsUpdate = !localFile.exists() || localFile.sha1 != sha
        if (needsUpdate) {
            onStatus("准备下载 $name...")
            val encodedName = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
            val ok = UpdateService.downloadAndReplaceCore(
                targetFile = localFile,
                downloadUrl = "${server.hqUrl}/update/ui/lib/$encodedName",
                expectedSha = sha,
                label = name,
                onDetail = onDetail
            )
            if (!ok) return PlatformUpdateSyncResult(false, updated)
            updated = true
            onStatus("$name 更新完成")
        }
    }

    val serverNames = serverEntries.keys
    val extraFiles = libDir.listFiles()
        ?.filter { it.isFile && it.name !in serverNames }
        ?: emptyList()
    if (extraFiles.isNotEmpty()) {
        onStatus("正在清理多余的库文件...")
        var deletedNow = 0
        var markedForLater = 0
        extraFiles.forEach { extra ->
            onDetail("删除: ${extra.name}")
            val (immediate, marked) = forceDelete(extra)
            if (immediate) {
                deletedNow++
            } else if (marked) {
                markedForLater++
                onDetail("${extra.name} 将在重启后删除")
            }
        }
        val msg = when {
            deletedNow > 0 && markedForLater > 0 -> "已删除 $deletedNow 个，$markedForLater 个将在重启后删除"
            deletedNow > 0 -> "已清理 $deletedNow 个多余的库文件"
            markedForLater > 0 -> "$markedForLater 个库文件将在重启后删除"
            else -> "清理完成"
        }
        onStatus(msg)
    }
    return PlatformUpdateSyncResult(true, updated)
}

internal actual fun replaceDownloadedUpdateFile(
    tempFile: File,
    targetFile: File,
    onDetail: (String) -> Unit
): Boolean {
    val parentDir = targetFile.absoluteFile.parentFile ?: File(".")

    fun deleteWithRetry(file: File, retries: Int = 5, delayMs: Long = 200): Boolean {
        repeat(retries) {
            if (!file.exists() || file.delete()) return true
            Thread.sleep(delayMs)
        }
        return !file.exists()
    }

    fun tryOverwriteEvenIfLocked(src: File, dst: File): Boolean = runCatching {
        FileInputStream(src).channel.use { inCh ->
            FileOutputStream(dst, false).channel.use { outCh ->
                outCh.truncate(0)
                var pos = 0L
                val size = inCh.size()
                while (pos < size) {
                    val transferred = inCh.transferTo(pos, 1024 * 1024, outCh)
                    if (transferred <= 0) break
                    pos += transferred
                }
            }
        }
        true
    }.getOrElse { false }

    val backupFile = if (targetFile.exists()) {
        File(parentDir, "${targetFile.name}.backup.${System.currentTimeMillis()}")
    } else null

    return runCatching {
        backupFile?.let { targetFile.copyTo(it, overwrite = true) }
        if (targetFile.exists() && !deleteWithRetry(targetFile)) {
            targetFile.deleteOnExit()
            val overwritten = tryOverwriteEvenIfLocked(tempFile, targetFile)
            if (!overwritten) {
                throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
            }
            tempFile.delete()
            return@runCatching
        }
        Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }.recoverCatching {
        if (targetFile.exists() && !deleteWithRetry(targetFile)) {
            targetFile.deleteOnExit()
            if (!tryOverwriteEvenIfLocked(tempFile, targetFile)) {
                throw IllegalStateException("无法删除旧文件: ${targetFile.absolutePath}")
            }
            tempFile.delete()
        } else {
            Files.copy(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            tempFile.delete()
        }
    }.onSuccess {
        backupFile?.delete()
    }.onFailure {
        onDetail("替换核心文件失败")
    }.isSuccess
}

private fun forceDelete(file: File): Pair<Boolean, Boolean> {
    if (System.getProperty("os.name").lowercase().contains("win")) {
        runCatching {
            val process = ProcessBuilder("cmd", "/c", "del", "/f", "/q", "\"${file.absolutePath}\"")
                .redirectErrorStream(true)
                .start()
            process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            process.destroyForcibly()
        }
        if (!file.exists()) return true to false
    }
    val truncated = runCatching {
        FileOutputStream(file).use { it.channel.truncate(0) }
        true
    }.getOrElse { false }
    file.deleteOnExit()
    return if (truncated) false to true else false to true
}
