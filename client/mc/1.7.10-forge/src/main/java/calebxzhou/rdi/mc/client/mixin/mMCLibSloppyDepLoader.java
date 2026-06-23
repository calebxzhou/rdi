package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.hook.MCLibNetworkBlockerLog;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "makamys.mclib.sloppydeploader.SloppyDepLoader", remap = false)
public class mMCLibSloppyDepLoader {
    @Inject(method = "preInit()V", at = @At("HEAD"), cancellable = true, remap = false)
    @Dynamic("Optional MCLib target")
    private void RDI$CancelSloppyDepLoaderPreInit(CallbackInfo ci) {
        MCLibNetworkBlockerLog.blocked("SloppyDepLoader.preInit");
        ci.cancel();
    }
}
