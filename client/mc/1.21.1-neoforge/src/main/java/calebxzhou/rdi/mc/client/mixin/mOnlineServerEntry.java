package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public class mOnlineServerEntry {
    @ModifyArgs(
            method = "refreshStatus",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                    ordinal = 0
            )
    )
    private void RDI$DisplayFixedPing(Args args) {
        Object[] translatableArgs = args.get(1);
        translatableArgs[0] = 11L;
    }

    @ModifyArgs(
            method = "getNarration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;",
                    ordinal = 3
            )
    )
    private void RDI$NarrateFixedPing(Args args) {
        Object[] translatableArgs = args.get(1);
        translatableArgs[0] = 11L;
    }
}
