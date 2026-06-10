package calebxzhou.rdi.mc.server.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RdiGlobalPlayerListPacket(String json) {
    private static final int MAX_JSON_LENGTH = 262_144;

    public static void encode(RdiGlobalPlayerListPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.json, MAX_JSON_LENGTH);
    }

    public static RdiGlobalPlayerListPacket decode(FriendlyByteBuf buf) {
        return new RdiGlobalPlayerListPacket(buf.readUtf(MAX_JSON_LENGTH));
    }

    public static void handle(RdiGlobalPlayerListPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().setPacketHandled(true);
    }
}
