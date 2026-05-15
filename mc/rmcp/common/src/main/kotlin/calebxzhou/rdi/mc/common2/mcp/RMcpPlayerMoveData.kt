package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlayerMoveData(
    val moved: Boolean,
    val from: REntityPosData,
    val to: REntityPosData,
    val distance: Double,
    val onGround: Boolean
)
