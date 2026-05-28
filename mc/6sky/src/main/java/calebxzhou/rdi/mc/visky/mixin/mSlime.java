package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * calebxzhou @ 2026-05-26 15:46
 */
@Mixin(Slime.class)
public class mSlime {
    @ModifyConstant(method = "setSize(IZ)V",constant = @Constant(floatValue = 0.2F))
    private float slimeSpeedUp(float constant){
        return 1f;
    }
}
