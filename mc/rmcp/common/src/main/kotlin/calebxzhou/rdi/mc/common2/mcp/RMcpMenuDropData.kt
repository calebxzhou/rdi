package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpMenuDropData(
    val dryRun: Boolean,
    val slot: Int,
    val requestedCount: Int,
    val droppedCount: Int,
    val changed: Boolean,
    val beforeSlot: RMcpMenuData.Slot,
    val afterSlot: RMcpMenuData.Slot,
    val menu: RMcpMenuData?
)
