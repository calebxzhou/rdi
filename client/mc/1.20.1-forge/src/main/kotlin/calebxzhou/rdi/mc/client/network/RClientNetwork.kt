package calebxzhou.rdi.mc.client.network

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import kotlin.concurrent.Volatile

object RClientNetwork {
    private const val PROTOCOL_VERSION = "1"

    @Volatile
    private var registered = false

    private val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("rdi", "general"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.messageBuilder(
            RGlobalPlayerListPacket::class.java,
            0,
            NetworkDirection.PLAY_TO_CLIENT
        )
            .encoder { packet, buf -> RGlobalPlayerListPacket.encode(packet, buf) }
            .decoder(RGlobalPlayerListPacket::decode)
            .consumerMainThread { packet, context -> RGlobalPlayerListPacket.handle(packet, context) }
            .add()
        CHANNEL.messageBuilder(
            RFirmSectionsPacket::class.java,
            1,
            NetworkDirection.PLAY_TO_CLIENT
        )
            .encoder { packet, buf -> RFirmSectionsPacket.encode(packet, buf) }
            .decoder(RFirmSectionsPacket::decode)
            .consumerMainThread { packet, context -> RFirmSectionsPacket.handle(packet, context) }
            .add()
    }
}
