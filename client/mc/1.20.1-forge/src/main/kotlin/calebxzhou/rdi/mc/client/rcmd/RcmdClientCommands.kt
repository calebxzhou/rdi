package calebxzhou.rdi.mc.client.rcmd

import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult
import calebxzhou.rdi.mc.rcmd.RcmdResult
import net.minecraft.client.Minecraft

object RcmdClientCommands {
    @JvmStatic
    fun isRcmd(message: String?): Boolean =
        message != null && message.startsWith("\\")

    @JvmStatic
    fun dispatch(minecraft: Minecraft, message: String): RcmdDispatchResult =
        calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.dispatch(RcmdClientBridge201(minecraft), message)

    @JvmStatic
    fun reply(minecraft: Minecraft, result: RcmdResult) {
        calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.reply(RcmdClientBridge201(minecraft), result)
    }
}
