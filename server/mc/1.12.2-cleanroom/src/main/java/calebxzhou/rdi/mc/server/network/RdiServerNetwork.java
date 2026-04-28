package calebxzhou.rdi.mc.server.network;

import calebxzhou.rdi.mc.common2.player.RGlobalPlayerList;
import com.google.gson.Gson;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class RdiServerNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_gplayers");
    private static final Gson GSON = new Gson();
    private static volatile boolean registered;
    private static volatile RdiGlobalPlayerListPacket lastPacket;

    private RdiServerNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(RdiGlobalPlayerListPacket.Handler.class, RdiGlobalPlayerListPacket.class, 0, Side.CLIENT);
    }

    public static void sendToAll(DedicatedServer server, RGlobalPlayerList playerList) {
        RdiGlobalPlayerListPacket packet = new RdiGlobalPlayerListPacket(GSON.toJson(playerList));
        lastPacket = packet;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sendTo(player, packet);
        }
    }

    public static void sendLastTo(EntityPlayerMP player) {
        RdiGlobalPlayerListPacket packet = lastPacket;
        if (packet != null) {
            sendTo(player, packet);
        }
    }

    private static void sendTo(EntityPlayerMP player, RdiGlobalPlayerListPacket packet) {
        CHANNEL.sendTo(packet, player);
    }
}
