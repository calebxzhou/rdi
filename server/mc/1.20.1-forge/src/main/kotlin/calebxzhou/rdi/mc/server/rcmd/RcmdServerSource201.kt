package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class RcmdServerSource201(val player: ServerPlayer) : RcmdSource {
    override fun name(): String = player.gameProfile.name

    override fun playerId(): UUID = player.uuid

    override fun hasPermission(permission: String): Boolean = player.hasPermissions(4)

    override fun sendFeedback(message: String) {
        player.sendSystemMessage(Component.literal(message))
    }

    override fun sendError(message: String) {
        player.sendSystemMessage(Component.literal("[rcmd] $message"))
    }
}
