package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.common.RGlobalPlayerList;
import calebxzhou.rdi.mc.server.firmsection.FirmSectionService;
import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;

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
                })
                .playToClient(RFirmSectionsPayload.TYPE, RFirmSectionsPayload.STREAM_CODEC, (payload, context) -> {
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

    public static void sendFirmSectionsToAll(MinecraftServer server) {
        var payload = firmSectionsPayload(server);
        for (var player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void sendFirmSectionsTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, firmSectionsPayload(player.server));
    }

    private static RFirmSectionsPayload firmSectionsPayload(MinecraftServer server) {
        var entries = new ArrayList<RFirmSectionsPayload.Entry>();
        for (var section : FirmSectionService.INSTANCE.all(server)) {
            entries.add(new RFirmSectionsPayload.Entry(
                    section.getDimensionId(),
                    section.getChunkX(),
                    section.getSectionY(),
                    section.getChunkZ()
            ));
        }
        return new RFirmSectionsPayload(entries);
    }
}
