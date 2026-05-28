package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2026-05-26 15:57
 */

@Mixin(AbstractSkeleton.class)
public abstract class mSkeleton {
    @Redirect(
            method = "reassessWeaponGoal",
            at= @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/AbstractSkeleton;getHardAttackInterval()I")
    )
    private static int RDI$BowAtkSpeed(AbstractSkeleton instance){
        return 2;
    }

}