package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbchunks.FTBChunksWorldConfig", remap = false)
public abstract class mFTBChunksWorldConfig {
    @Shadow(remap = false)
    @Final
    public static dev.ftb.mods.ftblibrary.snbt.config.IntValue MAX_CLAIMED_CHUNKS;

    @Shadow(remap = false)
    @Final
    public static dev.ftb.mods.ftblibrary.snbt.config.IntValue MAX_FORCE_LOADED_CHUNKS;

    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false)
    private static void rdi$lockDefaultChunkLimits(CallbackInfo ci) {
        MAX_CLAIMED_CHUNKS.set(128);
        MAX_FORCE_LOADED_CHUNKS.set(32);
    }

    @Inject(method = "getMaxClaimedChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rdi$lockMaxClaimedChunks(@Coerce Object playerData, @Coerce Object player, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(128);
    }

    @Inject(method = "getMaxForceLoadedChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private static void rdi$lockMaxForceLoadedChunks(@Coerce Object playerData, @Coerce Object player, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(32);
    }
}
