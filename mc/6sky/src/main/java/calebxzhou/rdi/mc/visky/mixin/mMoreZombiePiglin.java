package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * calebxzhou @ 2026-05-26 15:43
 */
@Mixin(NetherPortalBlock.class)
public class mMoreZombiePiglin {
    @ModifyConstant(method = "randomTick",constant = @Constant(intValue = 2000))
    private int randomTick(int original) {
        return 500;
    }
}
