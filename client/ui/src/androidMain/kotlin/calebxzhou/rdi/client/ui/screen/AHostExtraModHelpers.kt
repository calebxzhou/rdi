package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.McVersion
import java.io.File

actual fun selectHostExtraModFiles(): List<File>? = null

actual fun selectHostTaczFiles(): List<File>? = null

actual suspend fun matchHostExtraModFiles(
    files: List<File>,
    hostMcVersion: McVersion,
    onProgress: (String) -> Unit
): HostExtraModMatchResult {
    error("Host extra mod import not supported on Android")
}
