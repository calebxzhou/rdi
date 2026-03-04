package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.net.downloadFileFrom
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object UpdateService {
    private val lgr by Loggers
    /**
     * Force delete a file even if it's locked by another process.
     * Tries multiple strategies: normal delete, retry with delay, truncate to 0 bytes,
     * Windows force delete command, and finally deleteOnExit.
     */
    /**
     * Force delete a file. Returns Pair<immediatelyDeleted, markedForDeletion>
     */
    private fun forceDelete(file: File): Pair<Boolean, Boolean> {
        // Strategy 2: Try Windows force delete command
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
        
        // Strategy 3: Truncate file to 0 bytes (makes it useless even if can't delete)
        val truncated = runCatching {
            FileOutputStream(file).use { it.channel.truncate(0) }
            true
        }.getOrElse { false }
        
        // Strategy 4: Mark for deletion on JVM exit
        file.deleteOnExit()
        return false to true // Not deleted immediately, but marked for deletion
    }

    suspend fun startUpdateFlow(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit,
        onRestart: (suspend () -> Unit)? = null
    ) {
        runCatching {
            onStatus("正在检查更新...")
            val mcTargets = McVersion.entries.filter { it.enabled }.flatMap { mcVer ->
                mcVer.loaderVersions.keys.map { loader ->
                    val slug = "${mcVer.mcVer}-${loader.name.lowercase()}"
                    slug to DL_MOD_DIR.resolve("rdi-5-mc-client-$slug.jar").absoluteFile
                }
            }

            mcTargets.forEach { (slug, mcFile) ->
                val mcHash = server.makeRequest<String>("update/mc/$slug/hash").data
                    ?: throw RequestError("获取MC核心版本信息失败: $slug")

                val mcNeedsUpdate = !mcFile.exists() || mcFile.sha1 != mcHash
                if (mcNeedsUpdate) {
                    onStatus("准备下载 ${mcFile.name}...")
                    val mcUpdated = downloadAndReplaceCore(
                        targetFile = mcFile,
                        downloadUrl = "${server.hqUrl}/update/mc/$slug",
                        expectedSha = mcHash,
                        label = mcFile.name,
                        onDetail = onDetail
                    )
                    if (!mcUpdated) {
                        onStatus("更新失败，请检查网络")
                        return@runCatching
                    }
                    onStatus("${mcFile.name} 更新完成")
                }
            }

            val uiSync = syncUiLibs(onStatus, onDetail)
            if (!uiSync.first) {
                onStatus("更新失败，请检查网络")
                return@runCatching
            }
            //更新ui库后需要重启
            if (uiSync.second) {
                onStatus("更新完成，需要重启")
                onRestart?.invoke()
                return@runCatching
            }
            onStatus("当前已是最新版核心")
            onDetail("")
        }.onFailure {
            onStatus("更新流程遇到错误")
            it.printStackTrace()
            onDetail(it.message ?: "未知错误")
        }
    }

    private suspend fun downloadAndReplaceCore(
        targetFile: File,
        downloadUrl: String,
        expectedSha: String,
        label: String,
        onDetail: (String) -> Unit
    ): Boolean {
        val parentDir = targetFile.absoluteFile.parentFile ?: File(".")
        if (!parentDir.exists()) parentDir.mkdirs()
        val tempFile = File(parentDir, "${targetFile.name}.downloading.${System.currentTimeMillis()}")

        tempFile.toPath().downloadFileFrom(downloadUrl) { dl ->
            val totalBytes = dl.totalBytes
            val downloadedBytes = dl.bytesDownloaded.takeIf { it >= 0 } ?: 0L
            val percentValue = when {
                totalBytes > 0 -> downloadedBytes * 100.0 / totalBytes
                dl.fraction >= 0 -> dl.fraction*100.0
                else -> -1.0
            }

            val percentText = percentValue.takeIf { it >= 0 }
                ?.let { String.format("%.1f%%", it) } ?: "--"
            val downloadedText = downloadedBytes.takeIf { it > 0 }?.humanFileSize ?: "0B"
            val totalText = totalBytes.takeIf { it > 0 }?.humanFileSize ?: "--"
            val speedText = dl.speedBytesPerSecond.takeIf { it > 0 }
                ?.let { "${it / 1000}KB/s" } ?: "--"
            onDetail("$label $percentText $downloadedText/$totalText $speedText")
        }.getOrElse {
            it.printStackTrace()
            tempFile.delete()
            onDetail("下载失败，请检查网络后重试")
            return false
        }

        val downloadedSha = tempFile.sha1
        if (!downloadedSha.equals(expectedSha, true)) {
            tempFile.delete()
            onDetail("文件损坏了，请重下")
            return false
        }

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

        val backupFile = if (targetFile.exists()) File(
            parentDir,
            "${targetFile.name}.backup.${System.currentTimeMillis()}"
        ) else null

        val replaced = runCatching {
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
        }.isSuccess

        if (replaced) {
            backupFile?.delete()
            onDetail("核心文件已更新至最新版本。")
        }

        return replaced
    }

    private suspend fun syncUiLibs(
        onStatus: (String) -> Unit,
        onDetail: (String) -> Unit
    ): Pair<Boolean, Boolean> {
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
                val ok = downloadAndReplaceCore(
                    targetFile = localFile,
                    downloadUrl = "${server.hqUrl}/update/ui/lib/$encodedName",
                    expectedSha = sha,
                    label = name,
                    onDetail = onDetail
                )
                if (!ok) return false to updated
                updated = true
                onStatus("$name 更新完成")
            }
        }

        val serverNames = serverEntries.keys
        lgr.info { "服务器库列表: $serverNames" }
        val allLocalFiles = libDir.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
        lgr.info { "本地库列表: $allLocalFiles" }
        val extraFiles = libDir.listFiles()
            ?.filter { it.isFile && it.name !in serverNames }
            ?: emptyList()
        lgr.info { "需要删除的库: ${extraFiles.map { it.name }}" }
        
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

        return true to updated
    }
}
