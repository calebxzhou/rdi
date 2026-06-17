package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentString
import java.util.UUID

class RcmdCommandSenderSource112(private val sender: ICommandSender) : RcmdSource {
    val player: EntityPlayerMP?
        get() = sender.commandSenderEntity as? EntityPlayerMP

    override fun name(): String = sender.name

    override fun playerId(): UUID = player?.uniqueID ?: RcmdSource.NO_PLAYER_ID

    override fun hasPermission(permission: String): Boolean = sender.canUseCommand(4, permission)

    override fun sendFeedback(message: String) {
        sender.sendMessage(TextComponentString(message))
    }

    override fun sendError(message: String) {
        sender.sendMessage(TextComponentString("[rcmd] $message"))
    }
}
