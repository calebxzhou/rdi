package calebxzhou.rdi.mc.client.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPacketListener.class)
public interface AClientPacketListener {
    @Invoker("updateLevelChunk")
    void rdi$updateLevelChunk(int chunkX, int chunkZ, ClientboundLevelChunkPacketData data);

    @Invoker("applyLightData")
    void rdi$applyLightData(int chunkX, int chunkZ, ClientboundLightUpdatePacketData data);

    @Invoker("enableChunkLight")
    void rdi$enableChunkLight(LevelChunk chunk, int chunkX, int chunkZ);
}
