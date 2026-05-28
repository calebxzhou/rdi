package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * calebxzhou @ 2026-05-27 15:20
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class mSnowBreaking {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void RDI$SnowBreaksSlower(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockState state = (BlockState) (Object) this;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            cir.setReturnValue(cir.getReturnValue() * 6.0F);
        }
    }
}
