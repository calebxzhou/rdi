package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpPlayerData(
    val dim: String,
    val uuid: String,
    val name: String,
    val pos: REntityPosData,
    val health: Float,
    val maxHealth: Float,
    val food: Int,
    val gameMode: String,
    val latency: Int?
)
