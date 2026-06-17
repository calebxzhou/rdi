package calebxzhou.rdi.mc.server.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentString
import java.util.UUID

class RcmdServerSource112(val player: EntityPlayerMP) : RcmdSource {
    override fun name(): String = player.gameProfile.name

    override fun playerId(): UUID = player.uniqueID

    override fun hasPermission(permission: String): Boolean =
        player.server.playerList.canSendCommands(player.gameProfile)

    override fun sendFeedback(message: String) {
        player.sendMessage(TextComponentString(message))
    }

    override fun sendError(message: String) {
        player.sendMessage(TextComponentString("[rcmd] $message"))
    }
}
