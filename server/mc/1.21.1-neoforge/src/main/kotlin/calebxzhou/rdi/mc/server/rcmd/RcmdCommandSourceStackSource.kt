package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.function.Supplier

class RcmdCommandSourceStackSource(private val source: CommandSourceStack) : RcmdSource {
    val player: ServerPlayer?
        get() = source.player

    override fun name(): String {
        return source.textName
    }

    override fun playerId(): UUID {
        val player = this.player
        return if (player == null) RcmdSource.NO_PLAYER_ID else player.getUUID()
    }

    override fun hasPermission(permission: String?): Boolean {
        return source.hasPermission(4)
    }

    override fun sendFeedback(message: String) {
        source.sendSuccess(Supplier { Component.literal(message) }, false)
    }

    override fun sendError(message: String?) {
        source.sendFailure(Component.literal("[rcmd] " + message))
    }
}
