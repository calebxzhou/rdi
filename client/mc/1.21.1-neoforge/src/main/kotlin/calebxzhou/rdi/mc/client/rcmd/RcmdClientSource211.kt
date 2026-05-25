package calebxzhou.rdi.mc.client.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.*

class RcmdClientSource211(private val minecraft: Minecraft) : RcmdSource {
    override fun name(): String {
        return minecraft.getUser().getName()
    }

    override fun playerId(): UUID {
        return if (minecraft.player == null) RcmdSource.NO_PLAYER_ID else minecraft.player!!.getUUID()
    }

    override fun hasPermission(permission: String?): Boolean {
        return true
    }

    override fun sendFeedback(message: String) {
        sendMessage(message)
    }

    override fun sendError(message: String?) {
        sendMessage("[rcmd] $message")
    }

    private fun sendMessage(message: String) {
        val component = Component.literal(message)
        if (minecraft.player != null) {
            minecraft.player!!.displayClientMessage(component, false)
            return
        }
        minecraft.gui.getChat().addMessage(component)
    }
}
