package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.LightningGlowLichen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBolt.class)
public abstract class mLightningGlowLichen extends Entity {
    public mLightningGlowLichen(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    private BlockPos getStrikePosition() {
        throw new AssertionError();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LightningBolt;powerLightningRod()V"))
    private void RDI$ConvertVinesToGlowLichen(CallbackInfo ci) {
        LightningGlowLichen.tryConvert(level(), getStrikePosition());
    }
}
