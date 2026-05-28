package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2026-05-27 14:31
 */
@Mixin(Fox.class)
public class mFox {
    @Redirect(method = "populateDefaultEquipmentSlots",at= @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextFloat()F",ordinal = 0))
    private float RDI$AlwaysMouthItem(RandomSource randomSource) {
        return 0f;
    }
}
