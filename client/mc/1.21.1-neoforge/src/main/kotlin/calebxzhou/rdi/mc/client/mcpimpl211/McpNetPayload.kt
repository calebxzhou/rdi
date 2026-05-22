package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

private const val MAX_JSON_LENGTH = 2_097_152

data class McpNetPayload211(
    val packetJson: String,
) : CustomPacketPayload {
    constructor(buf: RegistryFriendlyByteBuf) : this(
        packetJson = buf.readUtf(MAX_JSON_LENGTH),
    )

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(packetJson, MAX_JSON_LENGTH)
    }

    fun c2sPacket(): McpC2SNetPacket {
        return json.decodeFromString(packetJson)
    }

    fun s2cPacket(): McpS2CNetPacket {
        return json.decodeFromString(packetJson)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<McpNetPayload211>(
            ResourceLocation.fromNamespaceAndPath("rdi", "mcp_game")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, McpNetPayload211> =
            CustomPacketPayload.codec(McpNetPayload211::write, ::McpNetPayload211)

        fun fromC2S(packet: McpC2SNetPacket): McpNetPayload211 {
            return McpNetPayload211(json.encodeToString(packet))
        }

        fun fromS2C(packet: McpS2CNetPacket): McpNetPayload211 {
            return McpNetPayload211(json.encodeToString(packet))
        }
    }
}

@EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
object McpNetPayload211Registry {
    @SubscribeEvent
    @JvmStatic
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1")
            .optional()
            .playBidirectional(
                McpNetPayload211.TYPE,
                McpNetPayload211.STREAM_CODEC,
                McpNetPayload211Registry::handlePayload,
            )
    }

    private fun handlePayload(payload: McpNetPayload211, context: IPayloadContext) {
        if (!context.flow().isClientbound) {
            return
        }
        context.enqueueWork {
            handleS2C(payload.s2cPacket(), context)
        }
    }

    private fun handleS2C(packet: McpS2CNetPacket, context: IPayloadContext) {
        McpGameImpl.complete(packet)
    }
}
