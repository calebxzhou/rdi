package calebxzhou.rdi.mc.server.mixin;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(C00PacketLoginStart.class)
class mAllowChineseNameLogin {

    @Shadow
    @Mutable
    private GameProfile field_149305_a;

    @Overwrite
    public void readPacketData(PacketBuffer data) throws IOException {
        String name = data.readStringFromBuffer(64);
        UUID profileId = data.readableBytes() >= 16 ? new UUID(data.readLong(), data.readLong()) : null;
        this.field_149305_a = new GameProfile(profileId, name);
    }
}

@Mixin(NetHandlerLoginServer.class)
abstract class mServerLoginPacketListener {

    @Shadow
    public abstract void func_147322_a(String reason);

    @Inject(method = "processLoginStart", at = @At("HEAD"), cancellable = true)
    private void RDI$RequireInjectedUuid(C00PacketLoginStart packetIn, CallbackInfo ci) {
        GameProfile profile = packetIn.func_149304_c();
        if (profile != null && profile.getId() != null) {
            return;
        }

        this.func_147322_a("未登录RDI账号！");
        ci.cancel();
    }
}
