package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpBlockEntityData(
    val pos: RBlockPos,
    val blockState: String,
    val type: String,
    val runtimeClass: String,
    val snbt: String?
)
