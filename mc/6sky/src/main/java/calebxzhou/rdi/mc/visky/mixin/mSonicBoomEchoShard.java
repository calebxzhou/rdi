package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.helper.SonicBoomEchoShard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SonicBoom.class)
public class mSonicBoomEchoShard {
    @Inject(method = "lambda$tick$2", at = @At("TAIL"), remap = false)
    private static void RDI$DropEchoShard(Warden warden, ServerLevel level, LivingEntity target, CallbackInfo ci) {
        SonicBoomEchoShard.tryDrop(target);
    }
}
