package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.SnifferEggDrowned;
import calebxzhou.rdi.mc.visky.helper.SuspiciousSniffer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * calebxzhou @ 2026-05-30 21:06
 */
@Mixin(Sniffer.class)
public class mSniffer {
    @Shadow
    private BlockPos getHeadBlock() {
        throw new AssertionError();
    }

    @Shadow
    private Stream<GlobalPos> getExploredPositions() {
        throw new AssertionError();
    }

    @Inject(method = "dropSeed", at = @At("HEAD"), cancellable = true)
    private void RDI$DropIronAndSusify(CallbackInfo ci) {
        if (SuspiciousSniffer.onDig((Sniffer) (Object) this, this.getHeadBlock())) {
            ci.cancel();
        }
    }

    @Inject(method = "canDig(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void RDI$CanDigSuspiciousBlocks(BlockPos digPos, CallbackInfoReturnable<Boolean> cir) {
        Sniffer sniffer = (Sniffer) (Object) this;
        GlobalPos globalDigPos = GlobalPos.of(sniffer.level().dimension(), digPos);
        if (this.getExploredPositions().noneMatch(globalDigPos::equals)
                && SuspiciousSniffer.isExtraDiggable(sniffer.level().getBlockState(digPos))
                && Optional.ofNullable(((Animal) (Object) this).getNavigation().createPath(digPos, 1)).map(Path::canReach).orElse(false)) {
            cir.setReturnValue(true);
        }
    }
}

@Mixin(RemoveBlockGoal.class)
class mRemoveBlockGoalSnifferEgg {
    @Shadow
    @Final
    private Mob removerMob;

    @Shadow
    @Final
    private Block blockToRemove;

    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"
            )
    )
    private boolean RDI$PlaceSnifferEgg(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        boolean removed = original.call(level, pos, moving);
        if (removed) {
            SnifferEggDrowned.tryPlaceSnifferEgg(level, pos, this.blockToRemove, this.removerMob);
        }
        return removed;
    }
}

@Mixin(TurtleEggBlock.class)
class mTurtleEggSnifferEgg {
    @Inject(
            method = "destroyEgg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/TurtleEggBlock;decreaseEggs(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"
            ),
            cancellable = true
    )
    private void RDI$KeepTurtleEggForSnifferEgg(Level level, BlockState state, BlockPos pos, Entity entity, int chance, CallbackInfo ci) {
        if (SnifferEggDrowned.shouldKeepTurtleEgg(entity)) {
            ci.cancel();
        }
    }
}

@Mixin(Drowned.class)
class mDrownedSnifferEgg {
    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void RDI$EquipSnifferEgg(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnGroupData,
            CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        SnifferEggDrowned.tryEquipSnifferEgg((Drowned) (Object) this);
    }
}
