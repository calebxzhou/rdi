package calebxzhou.rdi.client.service

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual suspend fun syncPlatformUpdates(
    onStatus: (String) -> Unit,
    onDetail: (String) -> Unit
): PlatformUpdateSyncResult = true to false

internal actual fun replaceDownloadedUpdateFile(
    tempFile: File,
    targetFile: File,
    onDetail: (String) -> Unit
): Boolean {
    return runCatching {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }
        Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }.recoverCatching {
        Files.copy(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        tempFile.delete()
    }.onFailure {
        tempFile.delete()
        onDetail("替换核心文件失败")
    }.isSuccess
}

