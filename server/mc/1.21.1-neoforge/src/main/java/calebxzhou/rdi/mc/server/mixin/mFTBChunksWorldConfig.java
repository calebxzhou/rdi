package calebxzhou.rdi.mc.server.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbchunks.FTBChunksWorldConfig", remap = false)
public interface mFTBChunksWorldConfig {
    @Inject(method = "getMaxClaimedChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rdi$lockMaxClaimedChunks(@Coerce Object playerData, @Coerce Object player, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(128);
    }

    @Inject(method = "getMaxForceLoadedChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rdi$lockMaxForceLoadedChunks(@Coerce Object playerData, @Coerce Object player, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(32);
    }
}
