package calebxzhou.rdi.mc.server.tpa

import calebxzhou.rdi.mc.rcmd.tpa.TpaPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.text.TextComponentString
import java.util.UUID

class TpaPlayer112(private val player: EntityPlayerMP) : TpaPlayer {
    fun unwrap(): EntityPlayerMP = player

    override fun id(): UUID = player.uniqueID

    override fun name(): String = player.gameProfile.name

    override fun sendMessage(message: String) {
        player.sendMessage(TextComponentString(message))
    }
}
