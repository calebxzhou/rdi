package calebxzhou.rdi.master.model

import kotlinx.serialization.Serializable

@Serializable
data class RChatMessage(
    val msgId: String,
    val sourceHostId: String? = null,
    val playerId: String,
    val playerName: String,
    val content: String,
    val timestamp: Long,
    val global: Boolean = true,
)
