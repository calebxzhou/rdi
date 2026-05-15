package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpMenuCloseData(
    val changed: Boolean,
    val wasOpen: Boolean,
    val before: RMcpMenuData,
    val after: RMcpMenuData?
)
