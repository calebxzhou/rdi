package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.chunkcache.RdiDelayedChunkCache;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.class)
public class mDelayedChunkCache {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    int viewDistance;

    @Shadow
    @Final
    private PlayerMap playerMap;

    @Inject(method = "move", at = @At("TAIL"))
    private void rdi$evictDelayedChunks(ServerPlayer player, CallbackInfo ci) {
        if (player.level() != level) {
            return;
        }
        RdiDelayedChunkCache.tick(player, viewDistance, pos -> {
            player.untrackChunk(pos);
            net.minecraftforge.event.ForgeEventFactory.fireChunkUnWatch(player, pos, level);
        });
    }

    @Inject(method = "updatePlayerStatus", at = @At("HEAD"))
    private void rdi$beginPlayerRemoval(ServerPlayer player, boolean track, CallbackInfo ci) {
        if (!track) {
            RdiDelayedChunkCache.beginImmediateUnload(player);
        }
    }

    @Inject(method = "updatePlayerStatus", at = @At("RETURN"))
    private void rdi$endPlayerRemoval(ServerPlayer player, boolean track, CallbackInfo ci) {
        if (!track) {
            RdiDelayedChunkCache.endImmediateUnload(player);
        }
    }

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void rdi$useDelayedChunkCache(
            ServerPlayer player,
            ChunkPos chunkPos,
            MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
            boolean wasLoaded,
            boolean load,
            CallbackInfo ci
    ) {
        if (player.level() != level) {
            return;
        }
        if (load && !wasLoaded && RdiDelayedChunkCache.consumeCached(player, chunkPos)) {
            ci.cancel();
            return;
        }
        if (!load && wasLoaded && RdiDelayedChunkCache.delayUnload(
                player,
                chunkPos,
                viewDistance,
                (pos, ticks) -> level.getChunkSource()
                        .addRegionTicket(RdiDelayedChunkCache.ticketType(), pos, 0, pos)
        )) {
            ci.cancel();
        }
    }

    @Inject(method = "getPlayers", at = @At("RETURN"), cancellable = true)
    private void rdi$includeDelayedChunkWatchers(
            ChunkPos pos,
            boolean boundaryOnly,
            CallbackInfoReturnable<List<ServerPlayer>> cir
    ) {
        List<ServerPlayer> players = new ArrayList<>(cir.getReturnValue());
        Set<ServerPlayer> trackedPlayers = playerMap.getPlayers(pos.toLong());
        for (ServerPlayer player : trackedPlayers) {
            if (!players.contains(player) && RdiDelayedChunkCache.isCached(player, pos)) {
                players.add(player);
            }
        }
        cir.setReturnValue(players);
    }
}
