package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpBlockStateBatchData(
    val dim: String,
    val requestedCount: Int,
    val blocks: List<RMcpBlockStateEntryData>
)
