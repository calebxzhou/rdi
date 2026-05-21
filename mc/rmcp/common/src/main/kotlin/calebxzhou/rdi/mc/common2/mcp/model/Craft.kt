package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-05-18 13:28
 */
@Serializable
data class CraftQ(
    val resultItemId: String,
    val takeFrom: List<ContainerSlotRef>,
    val outputTo: List<ContainerSlotRef>,
    val test: Boolean,
)

data class CraftP(

    val test: Boolean,
)
