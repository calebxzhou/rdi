package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.AnvilCoalToDiamond;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
public abstract class mFallingAnvilDiamond extends Entity {
    @Shadow
    private BlockState blockState;

    public mFallingAnvilDiamond(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void RDI$CompactCoalBlocksToDiamonds(float fallDistance, float multiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        AnvilCoalToDiamond.tryConvert(this, blockState);
    }
}
