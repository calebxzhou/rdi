package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpContainerPutData(
    val dryRun: Boolean,
    val fromInventorySlot: Int,
    val requestedCount: Int,
    val movedCount: Int,
    val movedItem: RMcpContainerData.Slot,
    val to: RMcpContainerMoveData.Endpoint?
)
