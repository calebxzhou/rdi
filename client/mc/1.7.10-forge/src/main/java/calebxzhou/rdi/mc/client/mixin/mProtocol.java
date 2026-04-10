package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.common.RDI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.handshake.client.C00Handshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(C00Handshake.class)
public class mProtocol {

    @Shadow
    private int field_149599_c;

    @Inject(method = "<init>(ILjava/lang/String;ILnet/minecraft/network/EnumConnectionState;)V", at = @At("TAIL"))
    private void RDI$InjectHostPort(int protocolVersion, String hostName, int port, EnumConnectionState intention, CallbackInfo ci) {
        this.field_149599_c = RDI.HOST_PORT;
    }

    @Redirect(
        method = "writePacketData",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketBuffer;writeShort(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf RDI$WriteCorrectPort(PacketBuffer buffer, int value) {
        return buffer.writeShort(RDI.HOST_PORT);
    }
}
