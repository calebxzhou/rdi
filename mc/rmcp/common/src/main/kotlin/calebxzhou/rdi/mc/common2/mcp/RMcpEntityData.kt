package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpEntityData(
    val dim: String,
    val uuid: String,
    val type: String,
    val name: String,
    val pos: REntityPosData,
    val distance: Double?
)
