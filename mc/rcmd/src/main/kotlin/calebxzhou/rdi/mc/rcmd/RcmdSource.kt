package calebxzhou.rdi.mc.rcmd

import java.util.UUID

interface RcmdSource {
    fun name(): String

    fun playerId(): UUID

    val isPlayer: Boolean
        get() = NO_PLAYER_ID != playerId()

    fun hasPermission(permission: String): Boolean

    fun sendFeedback(message: String)

    fun sendError(message: String)

    companion object {
        @JvmField
        val NO_PLAYER_ID: UUID = UUID(0L, 0L)
    }
}
