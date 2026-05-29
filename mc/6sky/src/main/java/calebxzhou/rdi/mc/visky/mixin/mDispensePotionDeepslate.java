package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeepslateConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/core/dispenser/DispenseItemBehavior$18")
public class mDispensePotionDeepslate {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void RDI$ConvertStoneToDeepslate(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!DeepslateConversion.isConversionPotion(potionContents)) {
            return;
        }

        ServerLevel level = source.level();
        BlockPos dispenserPos = source.pos();
        Direction dispenserFacing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = dispenserPos.relative(dispenserFacing);
        if (DeepslateConversion.convertWithBottle(level, targetPos, dispenserPos)) {
            cir.setReturnValue(new ItemStack(Items.GLASS_BOTTLE));
        }
    }
}
