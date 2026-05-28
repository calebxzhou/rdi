package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2026-05-26 00:26
 */
@Mixin(ServerLevel.class)
public class mMoreSnow {

    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = 48))
    private int moreSnow(int constant) {
        return 8;
    }

    @Redirect(
            method = "tickPrecipitation",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"
            )
    )
    private int snowAccumulationHeight(GameRules gameRules, GameRules.Key<GameRules.IntegerValue> key) {
        return 8;
    }
}
