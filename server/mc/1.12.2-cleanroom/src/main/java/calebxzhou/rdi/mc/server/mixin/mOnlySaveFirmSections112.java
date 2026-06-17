package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilChunkLoader.class)
public class mOnlySaveFirmSections112 {
    @Inject(method = "saveChunk", at = @At("HEAD"), cancellable = true)
    private void rdi$onlySaveFirmSections(World world, Chunk chunk, CallbackInfo ci) {
        if (FirmSectionService112.INSTANCE.shouldSaveChunk(world, chunk)) {
            return;
        }
        chunk.setModified(false);
        ci.cancel();
    }
}
