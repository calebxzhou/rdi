package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIMain;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-02-03 21:33
 */

@Mixin(GuiMainMenu.class)
public class mTitleScreen extends GuiScreen {
    @Redirect(method = "drawScreen",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;renderSkybox(IIF)V"))
    public void RDI$NOrenderPanorama(GuiMainMenu instance, int mouseX, int mouseY, float partialTicks) {
        this.mc.getTextureManager().bindTexture(RDIMain.BG_RES);
        GuiButton.drawModalRectWithCustomSizedTexture(0, 0,0f,0f, this.width, this.height,  this.width, this.height);
    }

    @Inject(method = "initGui",at= @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 0))
    public void RDI$AddButton(CallbackInfo ci){
        buttonList.add(RDIMain.JOIN_BUTTON);
    }
    @Inject(method = "actionPerformed",at=@At("TAIL"))
    public void RDI$OnClickButton(GuiButton button, CallbackInfo ci){
        if(button.id == RDIMain.JOIN_BUTTON_ID){
            RDIMain.onJoinRDI();
        }
    }
}
