package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DeepslateConversion;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownPotion.class)
public abstract class mThrownPotionDeepslate extends ThrowableItemProjectile {
    public mThrownPotionDeepslate(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "onHit", at = @At("HEAD"))
    private void RDI$ConvertStoneToDeepslate(HitResult result, CallbackInfo ci) {
        if (level().isClientSide) {
            return;
        }

        ItemStack itemStack = getItem();
        PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!DeepslateConversion.isConversionPotion(potionContents)) {
            return;
        }

        Vec3 hitPos = result.getType() == HitResult.Type.BLOCK ? result.getLocation() : position();
        if (itemStack.is(Items.LINGERING_POTION)) {
            AreaEffectCloud cloud = new AreaEffectCloud(level(), hitPos.x(), hitPos.y(), hitPos.z());
            cloud.setRadius(3.0F);
            cloud.setRadiusOnUse(-0.5F);
            cloud.setWaitTime(10);
            cloud.setDuration(cloud.getDuration() / 2);
            cloud.setRadiusPerTick(-cloud.getRadius() / (float) cloud.getDuration());
            cloud.setPotionContents(potionContents);
            level().addFreshEntity(cloud);
        } else if (itemStack.is(Items.SPLASH_POTION)) {
            DeepslateConversion.convertAtSplash(level(), hitPos);
        }
    }
}
