package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Optional;

/**
 * calebxzhou @ 2026-05-27 16:05
 */
@Mixin(PortalForcer.class)
public class mPortalForcer {
    @Shadow
    @Final
    protected ServerLevel level;

    @Unique
    private BlockState RDI$getPortalSkirtBlock(BlockPos pos) {
        if (level.getBiome(pos).is(Biomes.CRIMSON_FOREST)) {
            return Blocks.CRIMSON_NYLIUM.defaultBlockState();
        }
        if (level.getBiome(pos).is(Biomes.WARPED_FOREST)) {
            return Blocks.WARPED_NYLIUM.defaultBlockState();
        }
        return Blocks.NETHERRACK.defaultBlockState();
    }

    @Inject(
            method = "createPortal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Direction;getClockWise()Lnet/minecraft/core/Direction;",
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void RDI$AddNetherrackAroundVoidPortal(
            BlockPos pos,
            Direction.Axis axis,
            CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir,
            Direction direction,
            double d0,
            BlockPos portalBottomLeft,
            double d1,
            BlockPos fallbackPortalBottomLeft,
            WorldBorder worldBorder
    ) {
        if (!worldBorder.isWithinBounds(portalBottomLeft)) {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        Direction rotatedDirection = direction.getClockWise();
        for (int i = -1; i < 3; ++i) {
            for (int j = -2; j < 3; ++j) {
                if ((Math.abs(j) == 1 && (i == -1 || i == 2)) || (Math.abs(j) == 2 && (i == 0 || i == 1))) {
                    mutablePos.setWithOffset(
                            portalBottomLeft,
                            direction.getStepX() * i + rotatedDirection.getStepX() * j,
                            -1,
                            direction.getStepZ() * i + rotatedDirection.getStepZ() * j
                    );
                    level.setBlockAndUpdate(mutablePos, RDI$getPortalSkirtBlock(mutablePos));
                }
            }
        }
    }
}
