package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.common.RDI;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.CPacketLoginStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-02-03 23:08
 * 写入rdid与地图端口号
 */
@Mixin(CPacketLoginStart.class)
class mServerboundHelloPacket {
    @Shadow
    private GameProfile profile;

    @Inject(method = "writePacketData",at=@At("HEAD"))
    private void RDI$WriteUUIDPacket(PacketBuffer buffer, CallbackInfo ci){
        buffer.writeUniqueId(this.profile.getId());
    }
}
@Mixin(C00Handshake.class)
class mClientIntentionPacket {
    @Shadow
    @Mutable
    private int port;

    @Inject(method = "<init>(Ljava/lang/String;ILnet/minecraft/network/EnumConnectionState;Z)V",at=@At("TAIL"))
    private void RDI$InjectHostPort(String address, int port, EnumConnectionState state, boolean addFMLMarker, CallbackInfo ci){
        this.port = calebxzhou.rdi.mc.common.RDI.HOST_PORT;
    }
    @Redirect(method = "writePacketData",at= @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;writeShort(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf RDI$WriteCorrectPort(PacketBuffer buf, int value){
        buf.writeShort(RDI.HOST_PORT);
        return buf;
    }
}