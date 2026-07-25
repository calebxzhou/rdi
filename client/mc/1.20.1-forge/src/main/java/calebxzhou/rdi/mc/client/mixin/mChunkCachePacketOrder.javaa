package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.chunkcache.RdiChunkCacheClient;
import calebxzhou.rdi.mc.client.chunkcache.RdiChunkCacheClientHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientPacketListener.class)
public class mChunkCachePacketOrder {
    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void rdi$resetChunkCacheOrderBarrier(ClientboundRespawnPacket packet, CallbackInfo ci) {
        RdiChunkCacheClientHandler.clearDeferredPackets();
        RdiChunkCacheClient.advanceGeneration();
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void rdi$deferBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (defer(pos.getX() >> 4, pos.getZ() >> 4, () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), cancellable = true)
    private void rdi$deferSectionBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        SectionPos pos = ((ASectionBlocksUpdatePacket) packet).rdi$getSectionPos();
        if (defer(pos.x(), pos.z(), () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockEntityData", at = @At("HEAD"), cancellable = true)
    private void rdi$deferBlockEntityData(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (defer(pos.getX() >> 4, pos.getZ() >> 4, () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockEvent", at = @At("HEAD"), cancellable = true)
    private void rdi$deferBlockEvent(ClientboundBlockEventPacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (defer(pos.getX() >> 4, pos.getZ() >> 4, () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockDestruction", at = @At("HEAD"), cancellable = true)
    private void rdi$deferBlockDestruction(ClientboundBlockDestructionPacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        if (defer(pos.getX() >> 4, pos.getZ() >> 4, () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void rdi$deferLightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        if (defer(packet.getX(), packet.getZ(), () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("HEAD"), cancellable = true)
    private void rdi$deferForgetChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (defer(packet.getX(), packet.getZ(), () -> packet.handle((ClientPacketListener) (Object) this))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleChunksBiomes", at = @At("HEAD"), cancellable = true)
    private void rdi$deferChunkBiomes(ClientboundChunksBiomesPacket packet, CallbackInfo ci) {
        ClientPacketListener listener = (ClientPacketListener) (Object) this;
        List<ClientboundChunksBiomesPacket.ChunkBiomeData> immediate = new ArrayList<>();
        boolean deferred = false;
        for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
            ChunkPos pos = data.pos();
            if (RdiChunkCacheClientHandler.isAwaiting(pos.x, pos.z)) {
                deferred = true;
                RdiChunkCacheClientHandler.deferIfAwaiting(
                        pos.x,
                        pos.z,
                        () -> new ClientboundChunksBiomesPacket(List.of(data)).handle(listener)
                );
            } else {
                immediate.add(data);
            }
        }
        if (!deferred) {
            return;
        }
        if (!immediate.isEmpty()) {
            new ClientboundChunksBiomesPacket(immediate).handle(listener);
        }
        ci.cancel();
    }

    private boolean defer(int chunkX, int chunkZ, Runnable handler) {
        return RdiChunkCacheClientHandler.deferIfAwaiting(chunkX, chunkZ, handler);
    }
}
