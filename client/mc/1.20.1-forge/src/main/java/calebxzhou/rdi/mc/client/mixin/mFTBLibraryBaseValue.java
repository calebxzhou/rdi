package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftblibrary.snbt.config.BaseValue", remap = false)
public abstract class mFTBLibraryBaseValue {
    @Shadow(remap = false)
    public String key;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private void rdi$lockFTBChunkLimits(CallbackInfoReturnable<Object> cir) {
        if ("max_claimed_chunks".equals(key)) {
            cir.setReturnValue(128);
        } else if ("max_force_loaded_chunks".equals(key)) {
            cir.setReturnValue(32);
        }
    }
}
