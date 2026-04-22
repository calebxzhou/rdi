package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIClientHooks;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-04-09 23:31
 */
@Mixin(GuiMainMenu.class)
public class mTitleScreen extends GuiScreen {

    @Redirect(
        method = "drawScreen",
        at =
        @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiMainMenu;renderSkybox(IIF)V"))
    private void RDI$RenderCustomBackground(
        GuiMainMenu instance, int mouseX, int mouseY, float partialTicks) {
        this.mc.getTextureManager().bindTexture(RDIClientHooks.BG_RES);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(0.0D, this.height, this.zLevel, 0.0D, 1.0D);
        tessellator.addVertexWithUV(this.width, this.height, this.zLevel, 1.0D, 1.0D);
        tessellator.addVertexWithUV(this.width, 0.0D, this.zLevel, 1.0D, 0.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, this.zLevel, 0.0D, 0.0D);
        tessellator.draw();
    }

    @Inject(method = "addSingleplayerMultiplayerButtons", at = @At("TAIL"))
    private void RDI$AddJoinButton(int top, int rowHeight, CallbackInfo ci) {
        this.buttonList.add(
            RDIClientHooks.createJoinButton(
                this.width / 2 - RDIClientHooks.JOIN_BUTTON_WIDTH / 2, top - rowHeight));
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
