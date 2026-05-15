package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpStaringBlockData(
    val dim: String,
    val pos: RBlockPos,
    val id: String,
    val state: String,
    val fluid: RMcpFluidData?
)
