package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "rdi")
public final class RServerNetwork {
    private static final Gson GSON = new Gson();
    private static volatile RdiGlobalPlayerListPayload lastPayload;

    private RServerNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .optional()
                .playToClient(RdiGlobalPlayerListPayload.TYPE, RdiGlobalPlayerListPayload.STREAM_CODEC, (payload, context) -> {
                });
    }

    public static void sendToAll(DedicatedServer server, RGlobalPlayerList playerList) {
        var payload = new RdiGlobalPlayerListPayload(GSON.toJson(playerList));
        lastPayload = payload;
        for (var player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendLastTo(net.minecraft.server.level.ServerPlayer player) {
        var payload = lastPayload;
        if (payload != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
