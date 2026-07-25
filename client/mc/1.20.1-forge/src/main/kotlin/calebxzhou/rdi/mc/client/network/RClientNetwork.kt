package calebxzhou.rdi.mc.client.network

// import calebxzhou.rdi.mc.chunkcache.RChunkCacheManifestPacket
// import calebxzhou.rdi.mc.chunkcache.RChunkCacheReadyPacket
// import calebxzhou.rdi.mc.chunkcache.RChunkHashPacket
// import calebxzhou.rdi.mc.chunkcache.RChunkRequestPacket
// import calebxzhou.rdi.mc.client.chunkcache.RdiChunkCacheClientHandler
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import kotlin.concurrent.Volatile

object RClientNetwork {
    private const val PROTOCOL_VERSION = "3"

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
        /* CHANNEL.messageBuilder(
            RChunkCacheManifestPacket::class.java,
            2,
            NetworkDirection.PLAY_TO_SERVER
        )
            .encoder(RChunkCacheManifestPacket::encode)
            .decoder(RChunkCacheManifestPacket::decode)
            .consumerMainThread { _, context -> context.get().packetHandled = true }
            .add()
        CHANNEL.messageBuilder(
            RChunkCacheReadyPacket::class.java,
            3,
            NetworkDirection.PLAY_TO_SERVER
        )
            .encoder(RChunkCacheReadyPacket::encode)
            .decoder(RChunkCacheReadyPacket::decode)
            .consumerMainThread { _, context -> context.get().packetHandled = true }
            .add()
        CHANNEL.messageBuilder(
            RChunkHashPacket::class.java,
            4,
            NetworkDirection.PLAY_TO_CLIENT
        )
            .encoder(RChunkHashPacket::encode)
            .decoder(RChunkHashPacket::decode)
            .consumerMainThread { packet, context ->
                RdiChunkCacheClientHandler.handleChunkHash(packet)
                context.get().packetHandled = true
            }
            .add()
        CHANNEL.messageBuilder(
            RChunkRequestPacket::class.java,
            5,
            NetworkDirection.PLAY_TO_SERVER
        )
            .encoder(RChunkRequestPacket::encode)
            .decoder(RChunkRequestPacket::decode)
            .consumerMainThread { _, context -> context.get().packetHandled = true }
            .add() */
    }

    /* fun sendChunkCacheManifest(bytes: ByteArray, hasMore: Boolean) {
        CHANNEL.sendToServer(RChunkCacheManifestPacket(bytes, hasMore))
    }

    fun sendChunkCacheReady() {
        CHANNEL.sendToServer(RChunkCacheReadyPacket())
    }

    fun sendChunkRequest(chunkX: Int, chunkZ: Int, contentHash: Long, requestId: Long) {
        CHANNEL.sendToServer(RChunkRequestPacket(chunkX, chunkZ, contentHash, requestId))
    } */
}
