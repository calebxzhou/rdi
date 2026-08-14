package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.LoadingErrorScreen;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LoadingErrorScreen.class)
abstract class mLoadingErrorScreen {
    @Unique
    private static final Logger RDI$LOGGER = LoggerFactory.getLogger("rdi-mod-loading");

    @Shadow(remap = false)
    @Final
    private List<ModLoadingException> modLoadErrors;

    @Shadow(remap = false)
    @Final
    private List<ModLoadingWarning> modLoadWarnings;

    @Unique
    private boolean RDI$warningScreenSkipped;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void RDI$skipWarningOnlyScreen(CallbackInfo ci) {
        if (RDI$warningScreenSkipped || !modLoadErrors.isEmpty() || modLoadWarnings.isEmpty()) {
            return;
        }

        RDI$warningScreenSkipped = true;
        RDI$LOGGER.warn("模组加载产生{}条警告", modLoadWarnings.size());
        modLoadWarnings.forEach(warning -> RDI$LOGGER.warn("{}", warning.formatToString()));
        Minecraft.getInstance().setScreen(null);
        ci.cancel();
    }
}
