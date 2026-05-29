package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.BuddingAmethystConversion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFluid.class)
public class mBuddingAmethystLava {
    @Inject(method = "randomTick", at = @At("HEAD"))
    private void RDI$TryCreateBuddingAmethyst(Level level, BlockPos pos, FluidState state, RandomSource random, CallbackInfo ci) {
        BuddingAmethystConversion.tryConvert(level, pos, state, random);
    }
}
