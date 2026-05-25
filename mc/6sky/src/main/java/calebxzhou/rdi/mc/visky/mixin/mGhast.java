package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-25 23:38
 */
@Mixin(Ghast.class)
public class mGhast {
    //生成概率x4
    @ModifyConstant(method = "checkGhastSpawnRules",constant = @Constant(intValue = 20))
    private static int checkGhastSpawnRules(int constant) {
        return 4;
    }

    @Overwrite
    public int getMaxSpawnClusterSize() {
        return 8;
    }
}
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastMoveControl")
class mGhastMove{
    @ModifyConstant(
            method = "tick"
            ,constant = @Constant(doubleValue = 0.1D)
    )
    private static double change(double d){
        return 0.2D;
    }
}
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$RandomFloatAroundGoal")
class mGhastFly{
    @ModifyConstant(
            method = "start"
            ,constant = @Constant(doubleValue = 1.0D)
    )
    private static double change(double d){
        return 1.5D;
    }
}
@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
class mGhastShoot{
    @Shadow
    @Mutable
    public int chargeTime;
    @Shadow @Final
    private Ghast ghast;

    @ModifyConstant(
            method = "tick"
            ,constant = @Constant(intValue = 20)
    )
    private static int changeCD(int constant){
        return 11;
    }
    @ModifyConstant(
            method = "tick"
            ,constant = @Constant(doubleValue = 4.0D)
    )
    private static double changeSped(double constant){
        return 5.0D;
    }

    @Inject(
            method = "tick",
            at=@At("TAIL")
    )
    private void changeCd2(CallbackInfo ci){
        if(chargeTime<=-40){
            chargeTime=9;
            this.ghast.setCharging(true);
        }
    }
}