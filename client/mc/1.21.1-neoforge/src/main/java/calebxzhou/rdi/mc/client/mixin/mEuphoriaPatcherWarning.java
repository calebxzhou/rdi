package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
        targets = "com.euphoriapatches.euphoria_patcher.util.UserInstallErrorMessages",
        remap = false
)
public abstract class mEuphoriaPatcherWarning {
    @Redirect(
            method = "handleShaderNotFound(Lcom/euphoriapatches/euphoria_patcher/util/shader/ShaderVersionComparator;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/euphoriapatches/euphoria_patcher/EuphoriaPatcher;log(IILjava/lang/String;)V",
                    remap = false
            ),
            remap = false
    )
    private static void rdi$ignoreEuphoriaLog(int messageLevel, int messageFadeTimer, String message) {
    }

    @Redirect(
            method = "handleShaderNotFound(Lcom/euphoriapatches/euphoria_patcher/util/shader/ShaderVersionComparator;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/euphoriapatches/euphoria_patcher/util/UserInstallErrorMessages;copyLinkMessage()V",
                    remap = false
            ),
            remap = false
    )
    private static void rdi$ignoreCopyLinkMessage() {
    }
}
