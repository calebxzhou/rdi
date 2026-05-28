package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2026-05-26 15:50
 */
@Mixin(Zombie.class)
public class mZombie {
    //提高铲/剑爆率
    @Redirect(method = "populateDefaultEquipmentSlots",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F"))
    private float RDI$MustGiveEquip(RandomSource randomSource) {
        return 0f;
    }

}
