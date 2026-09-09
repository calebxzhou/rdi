package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public class mOnlineServerEntry {
    @ModifyArg(
            method = "refreshStatus",
            index = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                    ordinal = 0
            )
    )
    private Object[] RDI$DisplayFixedPing(Object[] translatableArgs) {
        translatableArgs[0] = 11L;
        return translatableArgs;
    }

    @ModifyArg(
            method = "getNarration",
            index = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                    ordinal = 3
            )
    )
    private Object[] RDI$NarrateFixedPing(Object[] translatableArgs) {
        translatableArgs[0] = 11L;
        return translatableArgs;
    }
}
