package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIClientHooks;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMultiplayer.class)
public class mMultiScreen extends GuiScreen {
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
