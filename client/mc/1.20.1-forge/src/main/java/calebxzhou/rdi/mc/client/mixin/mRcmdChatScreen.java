package calebxzhou.rdi.mc.client.mixin;

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
    private void rdi$sendRcmdOrChat(ClientPacketListener connection, String message) {
        if (!RcmdClientCommands.isRcmd(message)) {
            connection.sendChat(message);
            return;
        }
        var minecraft = Minecraft.getInstance();
        var result = RcmdClientCommands.dispatch(minecraft, message);
        if (!result.found()) {
            connection.sendChat(message);
            return;
        }
        RcmdClientCommands.reply(minecraft, result.result());
    }
}
