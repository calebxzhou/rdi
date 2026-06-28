package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.common.RDI;
import net.minecraft.world.SpawnerAnimals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnerAnimals.class)
public class mCancelWorldGenAnimalSpawning1710 {
    @Inject(method = "performWorldGenSpawning", at = @At("HEAD"), cancellable = true)
    private static void rdi$cancelWorldGenAnimalSpawning(CallbackInfo ci) {
        if (RDI.ONLY_SAVE_FIRM_SECTIONS) {
            ci.cancel();
        }
    }
}
