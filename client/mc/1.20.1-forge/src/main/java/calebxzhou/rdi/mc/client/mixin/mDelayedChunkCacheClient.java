package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.chunkcache.RdiChunkCacheConfig;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public class mDelayedChunkCacheClient {
    @Inject(method = "calculateStorageRange", at = @At("RETURN"), cancellable = true)
    private static void rdi$extendStorageRange(int viewDistance, CallbackInfoReturnable<Integer> cir) {
        if (RdiChunkCacheConfig.ENABLED && RdiChunkCacheConfig.DCC_DISTANCE > 0) {
            cir.setReturnValue(cir.getReturnValue() + RdiChunkCacheConfig.DCC_DISTANCE);
        }
    }
}
