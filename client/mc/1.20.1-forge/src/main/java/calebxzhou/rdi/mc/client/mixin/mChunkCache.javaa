package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.chunkcache.RdiChunkCacheClientHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class mChunkCache {
    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void rdi$cacheChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        RdiChunkCacheClientHandler.cachePacket(packet);
    }
}
