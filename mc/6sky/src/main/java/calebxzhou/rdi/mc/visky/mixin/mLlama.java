package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-26 15:41
 */
@Mixin(LlamaSpit.class)
public class mLlama {
    @Inject(
            method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            at=@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private void plusDamage(EntityHitResult entityHitResult, CallbackInfo ci){
        if(entityHitResult.getEntity() instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION,15*20,2));
            player.addEffect(new MobEffectInstance(MobEffects.POISON,15*20,2));
        }

    }
}
