package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * calebxzhou @ 2026-05-27 14:11
 */
@Mixin(WorldOptions.class)
public class mWorldOptions {
    @Mutable
    @Shadow
    @Final
    private long seed;

    @Inject(method = "<init>(JZZLjava/util/Optional;)V",at=@At("TAIL"))
    private void RDI$FixSeed(long seed, boolean generateStructures, boolean generateBonusChest, Optional legacyCustomOptions, CallbackInfo ci){
        this.seed = -7857578951488376789L;
    }
}
