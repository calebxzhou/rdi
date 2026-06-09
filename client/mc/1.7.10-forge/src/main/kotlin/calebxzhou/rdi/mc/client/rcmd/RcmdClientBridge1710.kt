package calebxzhou.rdi.mc.client.rcmd

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientBridge
import calebxzhou.rdi.mc.rcmd.RcmdSource
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import java.nio.file.Path
import java.util.UUID

class RcmdClientBridge1710(private val minecraft: Minecraft) : RcmdClientBridge {
    override fun name(): String = minecraft.session.username

    override fun playerId(): UUID =
        if (minecraft.thePlayer == null) RcmdSource.NO_PLAYER_ID else minecraft.thePlayer.uniqueID

    override fun hasPermission(permission: String): Boolean = true

    override fun sendFeedback(message: String) {
        sendMessage(message)
    }

    override fun sendError(message: String) {
        sendMessage("[rcmd] $message")
    }

    override fun gameDirectory(): Path = minecraft.mcDataDir.toPath()

    override fun executeOnMainThread(task: Runnable) {
        minecraft.func_152344_a(task)
    }

    override fun toggleSetFirmSectionsVisible(): Boolean {
        RDI.SHOW_SET_FIRM_SECTIONS = !RDI.SHOW_SET_FIRM_SECTIONS
        return RDI.SHOW_SET_FIRM_SECTIONS
    }

    override fun toggleNowFirmSectionVisible(): Boolean {
        RDI.SHOW_NOW_FIRM_SECTION = !RDI.SHOW_NOW_FIRM_SECTION
        return RDI.SHOW_NOW_FIRM_SECTION
    }

    private fun sendMessage(message: String) {
        val component = ChatComponentText(message)
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(component)
            return
        }
        minecraft.ingameGUI.chatGUI.printChatMessage(component)
    }
}
