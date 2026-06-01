package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.HugeMushroomMycelium;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHugeMushroomFeature.class)
public class mHugeMushroomMycelium {
    @Inject(method = "place", at = @At("TAIL"))
    private void RDI$SpreadMycelium(
        FeaturePlaceContext<HugeMushroomFeatureConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue()) {
            HugeMushroomMycelium.spread(context.level(), context.random(), context.origin());
        }
    }
}
