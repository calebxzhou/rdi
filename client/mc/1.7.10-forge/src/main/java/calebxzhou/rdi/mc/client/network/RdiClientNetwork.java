package calebxzhou.rdi.mc.client.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class RdiClientNetwork {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_gplist");
    private static volatile boolean registered;

    private RdiClientNetwork() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(RdiGlobalPlayerListPacket.Handler.class, RdiGlobalPlayerListPacket.class, 0, Side.CLIENT);
    }
}
