package calebxzhou.rdi.mc.client.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RdiClientNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static volatile boolean registered;

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("rdi", "global_player_list"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private RdiClientNetwork() {
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
}
