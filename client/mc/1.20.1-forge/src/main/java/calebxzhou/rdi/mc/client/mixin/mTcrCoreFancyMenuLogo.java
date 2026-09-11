package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
        targets = "com.p1nero.tcrcore.events.ClientModEvents",
        remap = false
)
public abstract class mTcrCoreFancyMenuLogo {
    @Inject(
            method = "checkFancyMenuLogo()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void rdi$disableFancyMenuLogoCheck(CallbackInfo ci) {
        ci.cancel();
    }
}
