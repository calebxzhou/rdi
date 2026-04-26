package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class mMinecraft {
    @Shadow
    public GuiScreen currentScreen;
    @Shadow
    public GameSettings gameSettings;
    @Shadow
    public EntityPlayerSP player;

    private boolean rdi$backslashDown;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen guiScreenIn);

    @Inject(method = "processKeyBinds()V", at = @At("HEAD"), cancellable = true)
    private void RDI$openRcmdChat(CallbackInfo ci) {
        boolean backslashDown = Keyboard.isKeyDown(Keyboard.KEY_BACKSLASH);
        if (!backslashDown) {
            rdi$backslashDown = false;
            return;
        }
        if (rdi$backslashDown) {
            return;
        }
        rdi$backslashDown = true;
        if (currentScreen != null || player == null || gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) {
            return;
        }
        displayGuiScreen(new GuiChat("\\"));
        ci.cancel();
    }
}
