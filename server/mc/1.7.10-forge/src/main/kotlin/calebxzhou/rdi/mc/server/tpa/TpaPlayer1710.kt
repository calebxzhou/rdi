package calebxzhou.rdi.mc.server.tpa

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ChatComponentText
import java.util.*

class TpaPlayer1710(private val player: EntityPlayerMP) : TpaPlayer {
    fun unwrap(): EntityPlayerMP {
        return player
    }

    override fun id(): UUID {
        return player.uniqueID
    }

    override fun name(): String {
        return player.commandSenderName
    }

    override fun sendMessage(message: String) {
        player.addChatMessage(ChatComponentText(message))
    }
}
