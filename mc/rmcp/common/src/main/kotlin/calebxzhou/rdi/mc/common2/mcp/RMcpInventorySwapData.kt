package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpInventorySwapData(
    val from: String,
    val to: String,
    val dryRun: Boolean,
    val changed: Boolean,
    val beforeFrom: RMcpInventoryData.Item,
    val beforeTo: RMcpInventoryData.Item,
    val afterFrom: RMcpInventoryData.Item,
    val afterTo: RMcpInventoryData.Item,
    val inventory: RMcpInventoryData?
)
