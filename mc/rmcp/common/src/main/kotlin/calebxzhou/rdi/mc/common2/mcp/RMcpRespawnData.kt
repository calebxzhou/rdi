package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpRespawnData(
    val wasDead: Boolean,
    val respawned: Boolean,
    val hardcore: Boolean,
    val beforeHealth: Float,
    val afterHealth: Float,
    val before: REntityPosData,
    val after: REntityPosData?
)
