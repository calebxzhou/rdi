package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.common.model.Mod

data class HostExtraModMatchResult(
    val matchedMods: List<Mod>,
    val rejectedFiles: List<String> = emptyList()
)

