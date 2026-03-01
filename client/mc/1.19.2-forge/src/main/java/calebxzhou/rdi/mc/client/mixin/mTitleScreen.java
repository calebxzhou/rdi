package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIMain;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static calebxzhou.rdi.mc.client.RDIMain.JOIN_BUTTON;

/**
 * calebxzhou @ 2025-04-15 10:32
 */
@Mixin(TitleScreen.class)
public class mTitleScreen extends Screen {
    @Shadow
    @Mutable @Final
    private static final ResourceLocation PANORAMA_OVERLAY = RDIMain.BG_RES;
    protected mTitleScreen(Component title) {
        super(title);
    }

    /*不用了 有的包有mixin冲突 @Redirect(method = "render",at= @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    public void RDI$NOrenderPanorama(PanoramaRenderer instance, float deltaT, float alpha) {

    }*/

    @Inject(method = "createNormalMenuOptions", at = @At("HEAD"))
    private void RDI$AddMultiplayerButton(int y, int rowHeight, CallbackInfo ci) {
         addRenderableWidget(JOIN_BUTTON);
    }

}
