package calebxzhou.rdi.client.ui

import calebxzhou.rdi.client.ui.comp.ConsoleState
import calebxzhou.rdi.common.model.McVersion

data class McPlayArgs(
    val title: String,
    val mcVer: McVersion,
    val versionId: String,
    val playArg: String,
)

object McPlayStore {
    var current: McPlayArgs? = null
    var onBack: (() -> Unit)? = null
    var process: Process? = null
    val consoleState: ConsoleState = ConsoleState()
}
