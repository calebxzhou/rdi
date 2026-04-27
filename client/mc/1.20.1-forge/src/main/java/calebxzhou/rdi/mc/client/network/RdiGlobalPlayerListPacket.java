package calebxzhou.rdi.mc.client.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RdiGlobalPlayerListPacket(String json) {
    private static final int MAX_JSON_LENGTH = 262_144;
    private static final Gson GSON = new Gson();

    public static void encode(RdiGlobalPlayerListPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.json, MAX_JSON_LENGTH);
    }

    public static RdiGlobalPlayerListPacket decode(FriendlyByteBuf buf) {
        return new RdiGlobalPlayerListPacket(buf.readUtf(MAX_JSON_LENGTH));
    }

    public static void handle(RdiGlobalPlayerListPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                GlobalPlayerListState.update(GSON.fromJson(packet.json, RGlobalPlayerList.class)));
        context.get().setPacketHandled(true);
    }
}
