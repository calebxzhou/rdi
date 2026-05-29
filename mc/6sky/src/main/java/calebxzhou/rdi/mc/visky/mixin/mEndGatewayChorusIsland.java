package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.EndGatewayChorusIsland;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(TheEndGatewayBlockEntity.class)
public class mEndGatewayChorusIsland {
    @Inject(
            method = "findOrCreateValidTeleportPos",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private static void RDI$AddChorusToGeneratedIsland(
            ServerLevel level,
            BlockPos pos,
            CallbackInfoReturnable<BlockPos> cir,
            @Local(ordinal = 2) BlockPos islandCenter
    ) {
        EndGatewayChorusIsland.generateChorus(level, islandCenter);
        cir.setReturnValue(EndGatewayChorusIsland.findGatewayLocation(level, islandCenter));
    }
}
