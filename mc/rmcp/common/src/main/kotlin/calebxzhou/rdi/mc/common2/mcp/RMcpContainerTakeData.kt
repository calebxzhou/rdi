package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerTakeData(
    val dryRun: Boolean,
    val requestedCount: Int,
    val movedCount: Int,
    val movedItem: RMcpContainerData.Slot,
    val from: RMcpContainerMoveData.Endpoint,
    val toInventorySlot: Int,
    val targetInventorySlots: List<Int>
)
