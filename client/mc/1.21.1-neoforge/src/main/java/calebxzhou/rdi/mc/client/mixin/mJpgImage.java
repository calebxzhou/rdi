package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.util.PngInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

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
