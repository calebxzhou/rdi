package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.rcmd.RcmdClientBridge112;
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands;
import calebxzhou.rdi.mc.rcmd.RcmdDispatchResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiScreen.class)
public class mRcmdChatScreen {
    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void rdi$handleClientRcmd(String message, boolean addToChat, CallbackInfo ci) {
        if (!((Object) this instanceof GuiChat)) {
            return;
        }
        RcmdClientBridge112 bridge = new RcmdClientBridge112(Minecraft.getMinecraft());
        RcmdDispatchResult result = RcmdClientCommands.dispatch(bridge, message);
        if (!result.found()) {
            return;
        }
        if (addToChat) {
            Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(message);
        }
        RcmdClientCommands.reply(bridge, result.result());
        ci.cancel();
    }
}
