package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-04-15 11:35
 */
@Mixin(MinecraftServer.class)
public class mTest {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "runServer",at=@At("HEAD"))
    private void RDI$Test(CallbackInfo ci){
        LOGGER.info("======RDI Test======");
    }
}
