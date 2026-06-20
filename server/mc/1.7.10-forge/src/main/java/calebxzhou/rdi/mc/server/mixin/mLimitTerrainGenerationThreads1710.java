package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.world.TerrainGenerationLimiter1710;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkProviderServer.class)
public class mLimitTerrainGenerationThreads1710 {
    @Redirect(
            method = "originalLoadChunk",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/IChunkProvider;provideChunk(II)Lnet/minecraft/world/chunk/Chunk;",
                    remap = true
            )
    )
    private Chunk rdi$limitTerrainGenerationThreads(IChunkProvider provider, int chunkX, int chunkZ) {
        TerrainGenerationLimiter1710.enter();
        try {
            return provider.provideChunk(chunkX, chunkZ);
        } finally {
            TerrainGenerationLimiter1710.exit();
        }
    }
}
