package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class RcmdCommandSourceStackSource(private val source: CommandSourceStack) : RcmdSource {
    val player: ServerPlayer?
        get() = source.entity as? ServerPlayer

    override fun name(): String = source.textName

    override fun playerId(): UUID = player?.uuid ?: RcmdSource.NO_PLAYER_ID

    override fun hasPermission(permission: String): Boolean = source.hasPermission(4)

    override fun sendFeedback(message: String) {
        source.sendSuccess({ Component.literal(message) }, false)
    }

    override fun sendError(message: String) {
        source.sendFailure(Component.literal("[rcmd] $message"))
    }
}
