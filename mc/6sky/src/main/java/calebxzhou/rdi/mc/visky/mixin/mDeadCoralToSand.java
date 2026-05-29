package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeadCoralToSand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseCoralPlantTypeBlock.class)
public abstract class mDeadCoralToSand extends Block {
    public mDeadCoralToSand(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (DeadCoralToSand.isDeadCoral(state)) {
            level.scheduleTick(pos, this, DeadCoralToSand.getDelay(level.getRandom()));
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Inject(method = "updateShape", at = @At("HEAD"))
    private void RDI$ScheduleDeadCoralSandDrop(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos, CallbackInfoReturnable<BlockState> cir) {
        if (DeadCoralToSand.isDeadCoral(state)) {
            level.scheduleTick(currentPos, this, DeadCoralToSand.getDelay(level.getRandom()));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (DeadCoralToSand.tryDropSand(state, level, pos, random)) {
            level.scheduleTick(pos, this, DeadCoralToSand.getDelay(random));
        }
    }
}
