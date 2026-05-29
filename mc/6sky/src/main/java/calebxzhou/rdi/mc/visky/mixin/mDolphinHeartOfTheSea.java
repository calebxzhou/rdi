package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DolphinFindHeartGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Dolphin;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Dolphin.class)
public abstract class mDolphinHeartOfTheSea extends WaterAnimal {
    protected mDolphinHeartOfTheSea(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    private Dolphin RDI$asDolphin() {
        return (Dolphin)(Object)this;
    }

    @ModifyArg(
            method = "registerGoals",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V",
                    ordinal = 2
            ),
            index = 1
    )
    private Goal RDI$ReplaceTreasureGoal(Goal original) {
        return new DolphinFindHeartGoal(RDI$asDolphin());
    }
}
