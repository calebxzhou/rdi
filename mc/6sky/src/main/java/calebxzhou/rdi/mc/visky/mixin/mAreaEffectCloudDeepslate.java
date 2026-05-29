package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeepslateConversion;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class mAreaEffectCloudDeepslate extends Entity {
    @Shadow
    private PotionContents potionContents;

    public mAreaEffectCloudDeepslate(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void RDI$ConvertStoneToDeepslate(CallbackInfo ci) {
        if (DeepslateConversion.isConversionPotion(potionContents)) {
            DeepslateConversion.convertInCloud(level(), getBoundingBox());
        }
    }
}
