package calebxzhou.rdi.mc.server.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.client.CPacketLoginStart;
import net.minecraft.server.network.NetHandlerLoginServer;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * calebxzhou @ 2026-04-18 18:00
 */
@Mixin(CPacketLoginStart.class)
class mAllowChineseNameLogin {
    @Shadow
    @Mutable
    private GameProfile profile;

    @Overwrite
    public void readPacketData(PacketBuffer buffer) throws IOException {
        this.profile = new GameProfile(buffer.readUniqueId(), buffer.readString(64));
    }
}

@Mixin(NetHandlerLoginServer.class)
abstract class mServerLoginPacketListener {
    @Shadow
    public abstract void disconnect(net.minecraft.util.text.ITextComponent reason);

    @Inject(method = "processLoginStart", at = @At("HEAD"), cancellable = true)
    private void RDI$RequireInjectedUuid(CPacketLoginStart packet, CallbackInfo ci) {
        if (packet.getProfile().getId() == null) {
            disconnect(new TextComponentString("未登录RDI账号！"));
            ci.cancel();
        }
    }
}
