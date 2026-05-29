package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeepslateConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionItem.class)
public class mPotionItemDeepslate {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void RDI$ConvertStoneToDeepslate(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack itemStack = context.getItemInHand();
        PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!DeepslateConversion.isConversionPotion(potionContents) || context.getClickedFace() == Direction.DOWN) {
            return;
        }

        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && DeepslateConversion.convertWithBottle(level, blockPos, blockPos)) {
            level.playSound(null, blockPos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(itemStack, player, new ItemStack(Items.GLASS_BOTTLE)));
            player.awardStat(Stats.ITEM_USED.get(itemStack.getItem()));
            cir.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
        }
    }
}
