package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIClientHooks;
import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public class mMultiScreen extends GuiScreen {
    @Shadow
    private ServerList field_146804_i;

    @Inject(
        method = "initGui",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ServerList;loadServerList()V",
            shift = At.Shift.AFTER))
    private void RDI$UseLocalServerList(CallbackInfo ci) {
        while (this.field_146804_i.countServers() > 0) {
            this.field_146804_i.removeServerData(0);
        }

        this.field_146804_i.addServerData(new ServerData(RDI.HOST_NAME, "127.0.0.1:55667"));
        this.field_146804_i.saveServerList();
    }

    @Inject(method = "func_146794_g", at = @At("TAIL"))
    private void RDI$AddJoinButton(CallbackInfo ci) {
        this.buttonList.add(
            RDIClientHooks.createJoinButton(
                Math.max(5, this.width - RDIClientHooks.JOIN_BUTTON_WIDTH - 5), 6));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void RDI$OnClickJoinButton(GuiButton button, CallbackInfo ci) {
        if (!RDIClientHooks.isJoinButton(button)) {
            return;
        }

        RDIClientHooks.joinHost(this);
        ci.cancel();
    }
}
