package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * calebxzhou @ 2026-05-25 23:01
 */
@Mixin(EntityBasedExplosionDamageCalculator.class)
public class mBlockEntityNeverBoom {

    @Shadow
    @Final
    private Entity source;

    @Inject(method = "shouldBlockExplode",at=@At("HEAD"), cancellable = true)
    public void RDI$shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float power, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(world.getBlockEntity(pos) == null && this.source.shouldBlockExplode(explosion, world, pos, state, power));
    }
}
