package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * calebxzhou @ 2026-05-26 15:48
 */
//女巫增强
@Mixin(Witch.class)
public class mWitch {

    @ModifyConstant(method = "createAttributes",constant = @Constant(doubleValue = 0.25))
    private static double witchSpeedUp(double constant) {
        return 1;
    }
    //缩短攻击间隔
    @ModifyConstant(method = "Lnet/minecraft/world/entity/monster/Witch;registerGoals()V",
            constant = @Constant(intValue = 60))
    private int intervalMinus(int constant){
        return 2;
    }
}
