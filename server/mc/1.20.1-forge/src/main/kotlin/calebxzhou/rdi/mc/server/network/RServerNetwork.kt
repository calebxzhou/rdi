package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RdiServerNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final Gson GSON = new Gson();
    private static volatile boolean registered;
    private static volatile RdiGlobalPlayerListPacket lastPacket;

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rdi", "global_player_list"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private RdiServerNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(RdiGlobalPlayerListPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RdiGlobalPlayerListPacket::encode)
                .decoder(RdiGlobalPlayerListPacket::decode)
                .consumerMainThread(RdiGlobalPlayerListPacket::handle)
                .add();
    }

    public static void sendToAll(DedicatedServer server, RGlobalPlayerList playerList) {
        var packet = new RdiGlobalPlayerListPacket(GSON.toJson(playerList));
        lastPacket = packet;
        for (var player : server.getPlayerList().getPlayers()) {
            sendTo(player, packet);
        }
    }

    public static void sendLastTo(ServerPlayer player) {
        var packet = lastPacket;
        if (packet != null) {
            sendTo(player, packet);
        }
    }

    private static void sendTo(ServerPlayer player, RdiGlobalPlayerListPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
