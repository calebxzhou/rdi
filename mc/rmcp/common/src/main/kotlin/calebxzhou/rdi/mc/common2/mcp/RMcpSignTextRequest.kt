package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpSignTextRequest(
    val pos: RBlockPos,
    val side: String,
    val text: String,
    val dryRun: Boolean
)
