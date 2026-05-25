package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.*

class RcmdServerSource211( val player: ServerPlayer) : RcmdSource {
    override fun name(): String {
        return player.gameProfile.name
    }

    override fun playerId(): UUID {
        return player.getUUID()
    }

    override fun hasPermission(permission: String): Boolean {
        return player.hasPermissions(2)
    }

    override fun sendFeedback(message: String) {
        player.sendSystemMessage(Component.literal(message))
    }

    override fun sendError(message: String) {
        player.sendSystemMessage(Component.literal("[rcmd] $message"))
    }
}
