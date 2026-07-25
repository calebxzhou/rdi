package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * calebxzhou @ 2024-06-23 22:07
 */

@Mixin(ChunkMap.class)
public class mSaveBandwidth {
    @Unique
    int tickAmount = 0;
    //3秒发一次掉落物的
    @Unique
    int sendTickAmount = 60;

   /* @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerEntity;sendChanges()V"))
    private void thottle(ServerEntity instance) {
        if(((AServerEntity)instance).getEntity() instanceof ItemEntity){
            if (tickAmount >= sendTickAmount) {
                instance.sendChanges();
                tickAmount = 0;
            } else {
                tickAmount++;
            }
        }else{
            instance.sendChanges();
        }
    }*/
}