package calebxzhou.rdi.mc.server.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * calebxzhou @ 2024-06-05 9:51
 */
public class mGuardTick {
}


@Mixin(MinecraftServer.class)
abstract
class mTickInvertServer {
    @Shadow
    @Final
    private List<Runnable> tickables;

    @Shadow
    public abstract void tickChildren(BooleanSupplier hasTimeLeft);


    @WrapOperation(method = "tickServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickChildren(Ljava/util/function/BooleanSupplier;)V"))
    private void tickServerChildrenNoCrash(MinecraftServer instance, BooleanSupplier bs, Operation<Void> original) {
        try {
            original.call(instance, bs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void RDI$OnTickLevel(ServerLevel serverlevel, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        try {
            original.call(serverlevel, hasTimeLeft);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
@Mixin(Level.class)
abstract
class mTickingEntity {
    @Redirect(method = "guardEntityTick", at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
    private <T extends Entity> void RDI$GuardEntityTick(Consumer<T> entityConsumer, Object t) {
        try {
            entityConsumer.accept((T) t);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
   /* @Overwrite
    public <T extends Entity> void guardEntityTick(Consumer<T> consumerEntity, T entity) {
        EntityTicker.tick(consumerEntity, entity);
    }*/
}

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
abstract
class mBoundTickingBlockEntityGuard {
    @WrapOperation(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntityTicker;tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)V"
            )
    )
    private <T extends BlockEntity> void RDI$TickBlockEntityGuard(
            BlockEntityTicker<T> ticker,
            Level level,
            BlockPos pos,
            BlockState state,
            T blockEntity,
            Operation<Void> original
    ) {
        try {
            original.call(ticker, level, pos, state, blockEntity);
        } catch (Throwable e) {
            e.printStackTrace();
            try {
                if (level != null && pos != null) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (blockEntity != null) {
                    blockEntity.setRemoved();
                }
            } catch (Throwable cleanupErr) {
                cleanupErr.printStackTrace();
            }
        }
    }
}

@Mixin(ServerLevel.class)
abstract
class mGuardServerLevelTick extends Level{

    private mGuardServerLevelTick(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, Supplier<ProfilerFiller> profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Shadow @Final private List<ServerPlayer> players;
    @WrapOperation(method = "tickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void tickBlock(
            BlockState blockState,
            ServerLevel serverLevel,
            BlockPos blockPos,
            RandomSource randomSource,
            Operation<Void> original
    ) {
        try {
            original.call(blockState, serverLevel, blockPos, randomSource);
        } catch (Exception e) {
            serverLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 0);
            e.printStackTrace();
        }

    }

    @WrapOperation(method = "tick",at= @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tickBlockEntities()V"))
    private void RDI$tickBlockEntties(ServerLevel level, Operation<Void> original){
        try {
            original.call(level);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

@Mixin(ServerChunkCache.class)
class mGuardChunkTick {
    @Shadow
    @Final
    private DistanceManager distanceManager;
    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Redirect(method = "runDistanceManagerUpdates()Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/DistanceManager;runAllUpdates(Lnet/minecraft/server/level/ChunkMap;)Z"))
    private boolean nocrashTick(DistanceManager instance, ChunkMap chunkStorage) {
        //return true;
        try {
            return this.distanceManager.runAllUpdates(this.chunkMap);
        } catch (Exception t) {
            t.printStackTrace();
        }
        return true;
    }

}

@Mixin(LevelTicks.class)
abstract
class mGuardLevelTick {
    @Shadow
    protected abstract void collectTicks(long gameTime, int maxAllowedTicks, ProfilerFiller profiler);

    @Shadow
    protected abstract void cleanupAfterTick();

    @Shadow
    protected abstract void runCollectedTicks(BiConsumer ticker);

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ticks/LevelTicks;collectTicks(JILnet/minecraft/util/profiling/ProfilerFiller;)V"))
    private void guardCollectTicks(LevelTicks instance, long gameTime, int maxAllowedTicks, ProfilerFiller profiler) {
        try {
            collectTicks(gameTime, maxAllowedTicks, profiler);
        } catch (Exception e) {
            e.printStackTrace();
            cleanupAfterTick();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/ticks/LevelTicks;runCollectedTicks(Ljava/util/function/BiConsumer;)V"))
    private void runCollectTicks(LevelTicks instance, BiConsumer ticker) {
        try {
            runCollectedTicks(ticker);
        } catch (Exception e) {
            e.printStackTrace();
            cleanupAfterTick();
        }
    }
}
