package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.hook.MCLibNetworkBlockerLog;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "makamys.mclib.sloppydeploader.SloppyDepLoaderAPI", remap = false)
public class mMCLibSloppyDepLoaderAPI {
    @Inject(
        method = "addDependenciesForMod(Ljava/lang/String;[Lmakamys/mclib/sloppydeploader/SloppyDependency;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @Dynamic("Optional MCLib target")
    private static void RDI$CancelAddDependenciesForMod(String modid, @Coerce Object sloppyDependencies, CallbackInfo ci) {
        MCLibNetworkBlockerLog.blocked("SloppyDepLoaderAPI.addDependenciesForMod modid=" + modid);
        ci.cancel();
    }
}
