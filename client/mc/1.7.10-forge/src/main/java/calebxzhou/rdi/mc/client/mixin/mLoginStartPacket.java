package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.common.RDI;

import java.util.UUID;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.client.C00PacketLoginStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(C00PacketLoginStart.class)
public class mLoginStartPacket {

    @Inject(method = "writePacketData", at = @At("TAIL"))
    private void RDI$WriteUuid(PacketBuffer data, CallbackInfo ci) {
        UUID profileId = RDI.PLAYER_ID;
        if (profileId == null) {
            return;
        }

        data.writeLong(profileId.getMostSignificantBits());
        data.writeLong(profileId.getLeastSignificantBits());
    }
}
