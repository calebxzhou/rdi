package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlaceRingRequest(
    val blockId: String,
    val startPos: RBlockPos,
    val endOffset: RBlockPos,
    val state: Map<String, String>,
    val dryRun: Boolean
)
