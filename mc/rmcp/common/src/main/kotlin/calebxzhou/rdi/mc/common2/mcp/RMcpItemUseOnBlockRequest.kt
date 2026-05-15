package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpItemUseOnBlockRequest(
    val pos: String,
    val face: String,
    val itemId: String,
    val fromInventorySlot: Int,
    val hand: String,
    val times: Int,
    val dryRun: Boolean
)
