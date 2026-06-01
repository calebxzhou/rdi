package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.humanFileSize
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.DL_MOD_DIR
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.net.downloadFileFrom
import calebxzhou.rdi.common.util.humanSpeed
import java.io.File
import java.security.MessageDigest

object UpdateService {
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

                val needsUpdate = !mcFile.exists() || mcFile.sha1Hex() != mcHash
                if (needsUpdate) {
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

            val platformSync = syncPlatformUpdates(onStatus, onDetail)
            if (!platformSync.success) {
                onStatus("更新失败，请检查网络")
                return@runCatching
            }
            if (platformSync.requiresRestart) {
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

    internal suspend fun downloadAndReplaceCore(
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
                dl.fraction >= 0 -> dl.fraction * 100.0
                else -> -1.0
            }

            val percentText = percentValue.takeIf { it >= 0 }
                ?.let { String.format("%.1f%%", it) } ?: "--"
            val downloadedText = downloadedBytes.takeIf { it > 0 }?.humanFileSize ?: "0B"
            val totalText = totalBytes.takeIf { it > 0 }?.humanFileSize ?: "--"
            val speedText = dl.speedBytesPerSecond.humanSpeed
            onDetail("$label $percentText $downloadedText/$totalText $speedText")
        }.getOrElse {
            it.printStackTrace()
            tempFile.delete()
            onDetail("下载失败，请检查网络后重试")
            return false
        }

        val downloadedSha = tempFile.sha1Hex()
        if (!downloadedSha.equals(expectedSha, true)) {
            tempFile.delete()
            onDetail("文件损坏了，请重下")
            return false
        }

        val replaced = replaceDownloadedUpdateFile(
            tempFile = tempFile,
            targetFile = targetFile,
            onDetail = onDetail
        )
        if (replaced) {
            onDetail("核心文件已更新至最新版本-")
        }
        return replaced
    }
}

typealias PlatformUpdateSyncResult = Pair<Boolean, Boolean>

private val PlatformUpdateSyncResult.success: Boolean
    get() = first

private val PlatformUpdateSyncResult.requiresRestart: Boolean
    get() = second

internal expect suspend fun syncPlatformUpdates(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit
): PlatformUpdateSyncResult

internal expect fun replaceDownloadedUpdateFile(
    tempFile: File,
    targetFile: File,
    onDetail: (String) -> Unit
): Boolean

private fun File.sha1Hex(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    inputStream().buffered().use { input ->
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
