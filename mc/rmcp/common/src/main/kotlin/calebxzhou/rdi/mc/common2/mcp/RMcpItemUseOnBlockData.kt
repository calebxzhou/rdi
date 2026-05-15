package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpItemUseOnBlockData(
    val action: String,
    val dryRun: Boolean,
    val pos: RBlockPos,
    val face: String,
    val requestedItemId: String,
    val requestedInventorySlot: Int,
    val hand: String,
    val requestedTimes: Int,
    val performedTimes: Int,
    val changedBlock: Boolean,
    val beforeBlockId: String,
    val afterBlockId: String,
    val beforeBlockState: String,
    val afterBlockState: String,
    val itemBefore: RMcpInventoryData.Item,
    val itemAfter: RMcpInventoryData.Item,
    val inventory: RMcpInventoryData?
)
