package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.chunkcache.RdiChunkCacheServer;
import calebxzhou.rdi.mc.server.chunkcache.RdiPendingChunkQueue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public class mChunkCache {
    @Inject(method = "playerLoadedChunk", at = @At("HEAD"), cancellable = true)
    private void rdi$queueUntilChunkCacheReady(
            ServerPlayer player,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
            LevelChunk chunk,
            CallbackInfo ci
    ) {
        if (RdiChunkCacheServer.shouldQueue(player)) {
            RdiPendingChunkQueue.enqueue(player.connection.connection, (ChunkMap) (Object) this, player, chunk);
            ci.cancel();
        }
    }

    @Redirect(
            method = "playerLoadedChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;trackChunk(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/network/protocol/Packet;)V")
    )
    private void rdi$sendHashWhenCached(ServerPlayer player, ChunkPos chunkPos, Packet<?> packet) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket
                && RdiChunkCacheServer.sendHashIfPossible(player, chunkPacket)) {
            return;
        }
        player.trackChunk(chunkPos, packet);
    }
}
