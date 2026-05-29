package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.DragonKillShulker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(EndDragonFight.class)
public class mDragonKillShulker {
    @Shadow
    private boolean previouslyKilled;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Nullable
    private BlockPos portalLocation;

    @Inject(
            method = "setDragonKilled",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/dimension/end/EndDragonFight;previouslyKilled:Z",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void RDI$SpawnShulkerOnDragonReKill(EnderDragon dragon, CallbackInfo ci) {
        if (previouslyKilled) {
            DragonKillShulker.spawn(level, portalLocation);
        }
    }
}
