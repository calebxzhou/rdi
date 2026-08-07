package calebxzhou.rdi.mc.client.mcpimpl

import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel
import kotlin.concurrent.Volatile

private const val MAX_JSON_LENGTH = 2_097_152

data class McpC2SPacket(val packetJson: String) {
    fun c2sPacket(): McpC2SNetPacket = json.decodeFromString(packetJson)

    companion object {
        @JvmStatic
        fun encode(packet: McpC2SPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.packetJson, MAX_JSON_LENGTH)
        }

        @JvmStatic
        fun decode(buf: FriendlyByteBuf): McpC2SPacket {
            return McpC2SPacket(buf.readUtf(MAX_JSON_LENGTH))
        }

        @JvmStatic
        fun fromC2S(packet: McpC2SNetPacket): McpC2SPacket {
            return McpC2SPacket(json.encodeToString(packet))
        }
    }
}

data class McpS2CPacket(val packetJson: String) {
    fun s2cPacket(): McpS2CNetPacket = json.decodeFromString(packetJson)

    companion object {
        @JvmStatic
        fun encode(packet: McpS2CPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.packetJson, MAX_JSON_LENGTH)
        }

        @JvmStatic
        fun decode(buf: FriendlyByteBuf): McpS2CPacket {
            return McpS2CPacket(buf.readUtf(MAX_JSON_LENGTH))
        }
    }
}

object McpNetwork {
    private const val PROTOCOL_VERSION = "1"

    @Volatile
    private var registered = false

    private val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("rdi", "mcp_game"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
    )

    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.messageBuilder(McpC2SPacket::class.java, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(McpC2SPacket::encode)
            .decoder(McpC2SPacket::decode)
            .consumerMainThread { _, context -> context.get().setPacketHandled(true) }
            .add()
        CHANNEL.messageBuilder(McpS2CPacket::class.java, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(McpS2CPacket::encode)
            .decoder(McpS2CPacket::decode)
            .consumerMainThread { packet, context ->
                McpGameImpl.complete(packet.s2cPacket())
                context.get().setPacketHandled(true)
            }
            .add()
    }

    fun sendToServer(packet: McpC2SNetPacket) {
        CHANNEL.sendToServer(McpC2SPacket.fromC2S(packet))
    }
}
