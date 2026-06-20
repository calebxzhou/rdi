package calebxzhou.rdi.mc.server.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.data.ForgePlayer;

@Mixin(targets = "serverutils.net.MessageClaimedChunksUpdate", remap = false)
public class mServerUtilitiesClaimedChunksUpdate1710 {
    @Inject(method = "getMaxClaimedChunks", at = @At("RETURN"), cancellable = true, remap = false)
    private void rdi$lockDisplayedMaxClaimChunks(ServerUtilitiesTeamData teamData, ForgePlayer player, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() >= 0) {
            cir.setReturnValue(128);
        }
    }
}
