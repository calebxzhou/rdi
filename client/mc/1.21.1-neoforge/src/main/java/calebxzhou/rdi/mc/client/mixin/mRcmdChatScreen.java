package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.rcmd.RcmdClientBridge211;
import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatScreen.class)
public class mRcmdChatScreen {
    @Redirect(
            method = "handleChatInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"
            )
    )
    private void RDI$sendRcmdOrChat(ClientPacketListener connection, String message) {
        if (!RcmdClientCommands.isRcmd(message)) {
            connection.sendChat(message);
            return;
        }
        var minecraft = Minecraft.getInstance();
        var bridge = new RcmdClientBridge211(minecraft);
        var result = calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.dispatch(bridge, message);
        if (!result.found()) {
            connection.sendChat(message);
            return;
        }
        calebxzhou.rdi.mc.common2.rcmd.client.RcmdClientCommands.reply(bridge, result.result());
    }
}
