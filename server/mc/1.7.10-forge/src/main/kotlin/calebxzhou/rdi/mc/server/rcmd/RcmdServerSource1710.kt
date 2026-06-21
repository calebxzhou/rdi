package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ChatComponentText
import java.util.*

class RcmdServerSource1710(val player: EntityPlayerMP) : RcmdSource {
    override fun name(): String {
        return player.getCommandSenderName()
    }

    override fun playerId(): UUID {
        return player.getUniqueID()
    }

    override fun hasPermission(permission: String): Boolean {
        return player.canCommandSenderUseCommand(2, permission)
    }

    override fun sendFeedback(message: String) {
        player.addChatMessage(ChatComponentText(message))
    }

    override fun sendError(message: String) {
        player.addChatMessage(ChatComponentText("[rcmd] " + message))
    }
}
