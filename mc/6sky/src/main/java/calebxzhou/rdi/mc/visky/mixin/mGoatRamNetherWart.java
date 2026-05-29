package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.GoatRamNetherWart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.RamTarget;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

@Mixin(RamTarget.class)
public abstract class mGoatRamNetherWart<E extends PathfinderMob> extends Behavior<E> {
    @Shadow
    @Final
    private Function<Goat, SoundEvent> getImpactSound;

    @Shadow
    protected abstract void finishRam(ServerLevel level, Goat goat);

    public mGoatRamNetherWart(Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        super(entryCondition);
    }

    @Inject(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/goat/Goat;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;getMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)Ljava/util/Optional;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private void RDI$BreakNetherWartBlock(ServerLevel level, Goat goat, long gameTime, CallbackInfo ci) {
        if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && GoatRamNetherWart.tryBreak(level, goat)) {
            level.playSound(null, goat, getImpactSound.apply(goat), SoundSource.NEUTRAL, 1.0F, 1.0F);
            finishRam(level, goat);
            ci.cancel();
        }
    }
}
