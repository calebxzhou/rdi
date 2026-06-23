package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.hook.MCLibNetworkBlockerLog;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "makamys.mclib.updatecheck.UpdateCheckAPI", remap = false)
public class mMCLibUpdateCheckAPI {
    @Inject(
        method = "submitTask(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    @Dynamic("Optional MCLib target")
    private void RDI$CancelUpdateCheck(String name, String currentVersion, String categoryID, String updateJSONUrl, CallbackInfo ci) {
        MCLibNetworkBlockerLog.blocked("UpdateCheckAPI.submitTask name=" + name + " url=" + updateJSONUrl);
        ci.cancel();
    }
}
