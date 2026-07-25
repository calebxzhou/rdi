package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public class mPlayerListDcc {
    @Redirect(
            method = "setViewDistance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;setViewDistance(I)V")
    )
    private void rdi$extendServerViewDistance(ServerChunkCache chunkCache, int viewDistance) {
        chunkCache.setViewDistance(viewDistance);
    }
}
