package calebxzhou.rdi.mc.client.gui

import java.util.UUID

@JvmRecord
data class RTabRow(@JvmField val text: String, @JvmField val playerId: UUID?, @JvmField val color: Int) {
    val isPlayer: Boolean
        get() = playerId != null
}
