package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeadCoralToSand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseCoralWallFanBlock.class)
public abstract class mDeadCoralWallFanToSand {
    @Inject(method = "updateShape", at = @At("HEAD"))
    private void RDI$ScheduleDeadCoralWallFanSandDrop(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos, CallbackInfoReturnable<BlockState> cir) {
        if (DeadCoralToSand.isDeadCoral(state)) {
            level.scheduleTick(currentPos, state.getBlock(), DeadCoralToSand.getDelay(level.getRandom()));
        }
    }
}
