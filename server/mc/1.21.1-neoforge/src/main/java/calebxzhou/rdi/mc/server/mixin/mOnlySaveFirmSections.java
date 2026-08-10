package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.world.TerrainCache211;
import com.mojang.datafixers.DataFixer;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkMap.class)
public abstract class mOnlySaveFirmSections {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private CompoundTag upgradeChunkTag(CompoundTag tag) {
        return null;
    }

    @Unique
    private Path rdi$dimensionPath;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rdi$captureDimensionPath(
            ServerLevel level,
            LevelStorageSource.LevelStorageAccess levelStorageAccess,
            DataFixer fixerUpper,
            StructureTemplateManager structureManager,
            Executor dispatcher,
            BlockableEventLoop<Runnable> mainThreadExecutor,
            LightChunkGetter lightChunk,
            ChunkGenerator generator,
            ChunkProgressListener progressListener,
            ChunkStatusUpdateListener chunkStatusListener,
            Supplier<DimensionDataStorage> overworldDataStorage,
            int viewDistance,
            boolean sync,
            CallbackInfo ci
    ) {
        rdi$dimensionPath = levelStorageAccess.getDimensionPath(level.dimension());
    }

    @Inject(method = "readChunk", at = @At("HEAD"), cancellable = true)
    private void rdi$readTerrainCacheChunk(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
        if (!TerrainCache211.INSTANCE.isEnabled()) {
            return;
        }

        cir.setReturnValue(((ChunkStorage) (Object) this).read(pos).thenApplyAsync(original -> {
            Optional<CompoundTag> tag = original.isPresent()
                    ? original
                    : TerrainCache211.read(level, rdi$dimensionPath, pos);
            return tag.map(this::upgradeChunkTag);
        }, Util.backgroundExecutor()));
    }
}
