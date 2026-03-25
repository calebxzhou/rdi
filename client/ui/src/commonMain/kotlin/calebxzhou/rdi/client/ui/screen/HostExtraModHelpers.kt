package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.McVersion
import java.io.File

data class HostExtraModMatchResult(
    val matchedMods: List<Mod>,
    val rejectedFiles: List<String> = emptyList()
)

expect fun selectHostExtraModFiles(): List<File>?

expect suspend fun matchHostExtraModFiles(
    files: List<File>,
    hostMcVersion: McVersion,
    onProgress: (String) -> Unit = {}
): HostExtraModMatchResult
