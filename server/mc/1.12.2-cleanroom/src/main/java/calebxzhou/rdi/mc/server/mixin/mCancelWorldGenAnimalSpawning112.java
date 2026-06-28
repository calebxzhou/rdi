package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.world.WorldEntitySpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldEntitySpawner.class)
public class mCancelWorldGenAnimalSpawning112 {
    @Inject(method = "performWorldGenSpawning", at = @At("HEAD"), cancellable = true)
    private static void rdi$cancelWorldGenAnimalSpawning(CallbackInfo ci) {
        if (RDI.ONLY_SAVE_FIRM_SECTIONS) {
            ci.cancel();
        }
    }
}
