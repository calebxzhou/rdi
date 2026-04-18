package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIMain;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-02-03 23:04
 */
@Mixin(GuiMultiplayer.class)
public class mMultiScreen extends GuiScreen {
    @Inject(method = "createButtons", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    public void RDI$AddButton(CallbackInfo ci) {
        buttonList.add(RDIMain.JOIN_BUTTON);
    }

    @Inject(method = "actionPerformed", at = @At("TAIL"))
    public void RDI$OnClickButton(GuiButton button, CallbackInfo ci) {
        if (button.id == RDIMain.JOIN_BUTTON_ID) {
            RDIMain.onJoinRDI();
        }
    }
}
