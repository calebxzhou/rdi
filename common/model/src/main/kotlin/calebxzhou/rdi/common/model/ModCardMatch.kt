package calebxzhou.rdi.common.model

import java.io.File

data class ModCardMatch(
    val mod: Mod,
    val card: Mod.CardVo? = null,
    val file: File? = null
)
