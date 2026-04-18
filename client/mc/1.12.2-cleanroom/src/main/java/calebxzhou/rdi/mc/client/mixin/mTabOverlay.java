package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2026-02-03 23:06
 */
@Mixin(GuiPlayerTabOverlay.class)
public class mTabOverlay {

    //永远显示头像
    @Redirect(method = "renderPlayerlist",
            at = @At(value = "INVOKE",target = "Lnet/minecraft/network/NetworkManager;isEncrypted()Z"))
    private boolean alwaysDisplayAvatar(NetworkManager instance){
        return true;
    }
}
