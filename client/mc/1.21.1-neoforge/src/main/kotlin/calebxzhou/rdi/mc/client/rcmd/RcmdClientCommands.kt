package calebxzhou.rdi.mc.client.rcmd;

import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult;
import calebxzhou.rdi.mc.rcmd.RcmdResult;
import net.minecraft.client.Minecraft;

public final class RcmdClientCommands {
    private RcmdClientCommands() {
    }

    public static boolean isRcmd(String message) {
        return message != null && message.startsWith("\\");
    }

    public static RcmdDispatchResult dispatch(Minecraft minecraft, String message) {
        return calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.dispatch(new RcmdClientBridge211(minecraft), message);
    }

    public static void reply(Minecraft minecraft, RcmdResult result) {
        calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.reply(new RcmdClientBridge211(minecraft), result);
    }
}
