package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(Minecraft.class)
public abstract class mScreenBan {
    @Unique
    private static final Set<String> RDI$BANNED_SCREEN_CLASSES = Set.of(
            "xaero.lib.client.gui.GuiUpdateAll",
            "org.merlin204.bfstart.client.CustomLoadingScreen"
    );

    @Shadow
    @Nullable
    public Screen screen;

    @Shadow
    @Nullable
    public ClientLevel level;

    @Unique
    private boolean RDI$redirectingToTitleScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void RDI$blockBannedScreen(@Nullable Screen target, CallbackInfo ci) {
        if (RDI$redirectingToTitleScreen || target == null
                || !RDI$BANNED_SCREEN_CLASSES.contains(target.getClass().getName())) {
            return;
        }

        ci.cancel();
        if (screen instanceof TitleScreen) {
            return;
        }

        RDI$redirectingToTitleScreen = true;
        try {
            ((Minecraft) (Object) this).setScreen(level == null ? null : new TitleScreen());
        } finally {
            RDI$redirectingToTitleScreen = false;
        }
    }
}
