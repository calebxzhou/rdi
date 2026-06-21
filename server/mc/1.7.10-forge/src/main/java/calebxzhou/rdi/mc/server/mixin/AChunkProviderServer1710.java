package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkProviderServer.class)
public interface AChunkProviderServer1710 {
    @Invoker("safeSaveChunk")
    void rdi$safeSaveChunk(Chunk chunk);
}
