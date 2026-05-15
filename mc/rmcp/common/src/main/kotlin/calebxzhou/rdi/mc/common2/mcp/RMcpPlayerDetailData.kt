package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlayerDetailData(
    val brief: RMcpPlayerData,
    val entity: RMcpEntityDetailData,
    val profile: Map<String, Any?>,
    val player: Map<String, Any?>,
    val inventory: Map<String, Any?>?
)
