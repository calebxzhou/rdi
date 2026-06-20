package calebxzhou.rdi.mc.server.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "serverutils.data.ServerUtilitiesTeamData", remap = false)
public class mServerUtilitiesTeamData1710 {
    @Inject(method = "getMaxClaimChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private void rdi$lockMaxClaimChunks(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) {
            cir.setReturnValue(128);
        }
    }

    @Inject(method = "getMaxChunkloaderChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private void rdi$lockMaxChunkloaderChunks(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) {
            cir.setReturnValue(32);
        }
    }
}
