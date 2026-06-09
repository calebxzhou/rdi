package calebxzhou.rdi.mc.rcmd.chat


data class RChatMessage(
    val msgId: String,
    val sourceHostId: String,
    val playerId: String,
    @JvmField val playerName: String,
    @JvmField val content: String,
    val timestamp: Long,
    val global: Boolean
)
