package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
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
    public EntityClientPlayerMP thePlayer;

    @Shadow
    public abstract void displayGuiScreen(GuiScreen guiScreen);

    @Inject(
        method = "runTick",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/FMLCommonHandler;fireKeyInput()V"))
    private void RDI$OpenRcmdChat(CallbackInfo ci) {
        if (this.currentScreen != null || this.thePlayer == null) {
            return;
        }
        if (this.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) {
            return;
        }
        if (Keyboard.getEventKeyState()
            && (Keyboard.getEventCharacter() == '\\' || Keyboard.getEventKey() == Keyboard.KEY_BACKSLASH)) {
            this.displayGuiScreen(new GuiChat("\\"));
        }
    }
}
