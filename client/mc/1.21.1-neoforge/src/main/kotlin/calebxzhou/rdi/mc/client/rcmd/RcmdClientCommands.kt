package calebxzhou.rdi.mc.client.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult
import calebxzhou.rdi.mc.rcmd.RcmdResult
import net.minecraft.client.Minecraft

object RcmdClientCommands {
    @JvmStatic
    fun isRcmd(message: String?): Boolean {
        return message != null && message.startsWith("\\")
    }

    fun dispatch(minecraft: Minecraft, message: String): RcmdDispatchResult {
        return calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.dispatch(RcmdClientBridge211(minecraft), message)
    }

    fun reply(minecraft: Minecraft, result: RcmdResult) {
        calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.reply(RcmdClientBridge211(minecraft), result)
    }
}
