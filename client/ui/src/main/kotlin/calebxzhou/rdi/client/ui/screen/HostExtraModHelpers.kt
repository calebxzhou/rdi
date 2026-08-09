package calebxzhou.rdi.client.ui.screen

import calebxzhou.rdi.client.model.UiMod

data class HostExtraModMatchResult(
    val matchedMods: List<UiMod>,
    val rejectedFiles: List<String> = emptyList()
)

