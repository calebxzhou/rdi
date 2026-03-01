package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * calebxzhou @ 2026-02-14 13:28
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class mPlusTimeout {
    /*@ModifyConstant(method = "tick",constant = @Constant(longValue = 15000L))
    private static long plusTimeout(long constant){
        return 60000L;
    }*/
}
