package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-27 14:31
 */
@Mixin(Fox.class)
public abstract class mFox extends Mob {
    protected mFox(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Redirect(method = "populateDefaultEquipmentSlots",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F",ordinal = 0))
    private float RDI$AlwaysMouthItem(RandomSource randomSource) {
        return 0f;
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F", ordinal = 1), cancellable = true)
    private void RDI$SweetBerriesMouthItem(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        if (random.nextFloat() < 0.2f) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
            ci.cancel();
        }
    }
}
