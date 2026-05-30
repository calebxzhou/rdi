package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeadBushConversion;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionItem.class)
public class mDeadBushToBush {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void RDI$HydrateDeadBush(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (DeadBushConversion.tryHydrateDeadBush(context)) {
            cir.setReturnValue(InteractionResult.sidedSuccess(context.getLevel().isClientSide));
        }
    }
}
