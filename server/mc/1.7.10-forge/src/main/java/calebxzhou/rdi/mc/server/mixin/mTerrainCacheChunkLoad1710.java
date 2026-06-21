package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.firmsection.FirmSectionService1710;
import calebxzhou.rdi.mc.server.world.TerrainCache1710;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkProviderServer.class)
public abstract class mTerrainCacheChunkLoad1710 {
    @Shadow
    public WorldServer worldObj;

    @Shadow
    public IChunkProvider currentChunkProvider;

    @Shadow
    private Chunk safeLoadChunk(int chunkX, int chunkZ) {
        return null;
    }

    @Redirect(
            method = "originalLoadChunk",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/ChunkProviderServer;safeLoadChunk(II)Lnet/minecraft/world/chunk/Chunk;",
                    remap = true
            )
    )
    private Chunk rdi$loadTerrainCacheChunk(ChunkProviderServer provider, int chunkX, int chunkZ) {
        Chunk chunk = safeLoadChunk(chunkX, chunkZ);
        if (chunk != null) {
            return chunk;
        }

        if (!TerrainCache1710.INSTANCE.isEnabled()) {
            return null;
        }
        if (FirmSectionService1710.INSTANCE.hasFirmChunk(worldObj, chunkX, chunkZ)) {
            return null;
        }

        Chunk cached = TerrainCache1710.loadChunk(worldObj, chunkX, chunkZ);
        if (cached != null) {
            cached.lastSaveTime = worldObj.getTotalWorldTime();
            if (currentChunkProvider != null) {
                currentChunkProvider.recreateStructures(chunkX, chunkZ);
            }
        }
        return cached;
    }
}
