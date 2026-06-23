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
@Mixin(targets = "makamys.mclib.ext.assetdirector.AssetDirectorAPI", remap = false)
public class mMCLibAssetDirectorAPI {
    @Inject(
        method = "register(Lmakamys/mclib/ext/assetdirector/ADConfig;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @Dynamic("Optional MCLib target")
    private static void RDI$CancelAssetDirectorRegister(@Coerce Object config, CallbackInfo ci) {
        MCLibNetworkBlockerLog.blocked("AssetDirectorAPI.register");
        ci.cancel();
    }
}
