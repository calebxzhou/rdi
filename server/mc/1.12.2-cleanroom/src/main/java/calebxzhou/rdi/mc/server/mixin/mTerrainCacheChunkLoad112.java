package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.world.TerrainCache112;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkProviderServer.class)
public abstract class mTerrainCacheChunkLoad112 {
    @Shadow
    @Final
    public WorldServer world;

    @Shadow
    @Final
    public IChunkGenerator chunkGenerator;

    @Shadow
    @Final
    public Long2ObjectMap<Chunk> loadedChunks;

    @Shadow
    public abstract Chunk getLoadedChunk(int chunkX, int chunkZ);

    @Inject(method = "loadChunk(IILjava/lang/Runnable;)Lnet/minecraft/world/chunk/Chunk;", at = @At("RETURN"), cancellable = true)
    private void rdi$loadTerrainCacheChunk(int chunkX, int chunkZ, Runnable runnable, CallbackInfoReturnable<Chunk> cir) {
        if (cir.getReturnValue() != null || !TerrainCache112.INSTANCE.isEnabled()) {
            return;
        }

        Chunk loaded = getLoadedChunk(chunkX, chunkZ);
        if (loaded != null) {
            cir.setReturnValue(loaded);
            return;
        }

        Chunk cached = TerrainCache112.loadChunk(world, chunkX, chunkZ);
        if (cached != null) {
            cached.setLastSaveTime(world.getTotalWorldTime());
            chunkGenerator.recreateStructures(cached, chunkX, chunkZ);
            loadedChunks.put(ChunkPos.asLong(chunkX, chunkZ), cached);
            cached.onLoad();
            cached.populate((ChunkProviderServer) (Object) this, chunkGenerator);
            cir.setReturnValue(cached);
        }
    }
}
