package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-27 15:36
 */
@Mixin(RangedBowAttackGoal.class)
public class mRangedBowAttackGoal {
    @Shadow
    private int attackIntervalMin;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/Mob;DIF)V", at = @At("TAIL"))
    private void RDI$FastBowAttackInterval(Mob mob, double speedModifier, int attackIntervalMin, float attackRadius, CallbackInfo ci) {
        this.attackIntervalMin = 2;
    }

    @Inject(method = "setMinAttackInterval", at = @At("TAIL"))
    private void RDI$FastBowAttackInterval(int attackCooldown, CallbackInfo ci) {
        this.attackIntervalMin = 2;
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 20, ordinal = 2))
    private int RDI$FastBowDraw(int constant) {
        return 2;
    }
}
