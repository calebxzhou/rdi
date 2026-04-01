package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.common.JpegUtils;
import calebxzhou.rdi.mc.common.RDI;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.PngInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * calebxzhou @ 8/17/2025 3:38 PM
 */
@Mixin(PngInfo.class)
public class mJpgImage {
    @Inject(method = "validateHeader",at=@At("HEAD"), cancellable = true)
    private static void RDI$NoValidatePng(ByteBuffer buffer, CallbackInfo ci){
        ci.cancel();
    }
}
