package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * calebxzhou @ 2026-05-26 19:21
 */
@Mixin(Warden.class)
public class mWarden {

    @ModifyConstant(method = "createAttributes",constant = @Constant(doubleValue = 1.5))
    private static double RDI$MoreKnockback(double value) {
        return 5.0;
    }
    @ModifyConstant(method = "createAttributes",constant = @Constant(doubleValue = 30.0))
    private static double RDI$MoreDamage(double value) {
        return 50.0;
    }
}
