package calebxzhou.rdi.mc.server.mcpimpl211

import calebxzhou.rdi.mc.common2.mcp.*
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import calebxzhou.rdi.mc.server.mcpimpl211.handler.BlockHandler211
import calebxzhou.rdi.mc.server.mcpimpl211.handler.ContainerHandler
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext

private const val MAX_JSON_LENGTH = 2_097_152

data class GameNetPayload211(
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
        val TYPE = CustomPacketPayload.Type<GameNetPayload211>(
            ResourceLocation.fromNamespaceAndPath("rdi", "mcp_game")
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, GameNetPayload211> =
            CustomPacketPayload.codec(GameNetPayload211::write, ::GameNetPayload211)

        fun fromC2S(packet: McpC2SNetPacket): GameNetPayload211 {
            return GameNetPayload211(json.encodeToString(packet))
        }

        fun fromS2C(packet: McpS2CNetPacket): GameNetPayload211 {
            return GameNetPayload211(json.encodeToString(packet))
        }
    }
}

@EventBusSubscriber(modid = "rdi")
object GameNetPayload211Registry {
    private val C2S_HANDLERS: Map<String, (String, ServerPlayer) -> Any> = mapOf(
        c2sHandler<BlockPlaceBoxQ> { req, player ->
            BlockHandler211.handleBox(req, player).getOrThrow()
        },
        c2sHandler<BlockPlaceDiscreteQ> { req, player ->
            BlockHandler211.handleDiscrete(req, player).getOrThrow()
        },
        c2sHandler<ContainerSlotListQ> { req, player ->
            ContainerHandler.slotList(req, player).getOrThrow()
        },
        c2sHandler<ContainerMoveQ> { req, player ->
            ContainerHandler.move(req, player).getOrThrow()
        },
    )

    @SubscribeEvent
    @JvmStatic
    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar("1")
            .optional()
            .playBidirectional(
                GameNetPayload211.TYPE,
                GameNetPayload211.STREAM_CODEC,
                GameNetPayload211Registry::handlePayload,
            )
    }

    private fun handlePayload(payload: GameNetPayload211, context: IPayloadContext) {
        if (!context.flow().isServerbound) {
            return
        }
        context.enqueueWork {
            handleC2S(payload.c2sPacket(), context)
        }
    }

    private fun handleC2S(packet: McpC2SNetPacket, context: IPayloadContext) {
        val resp = runCatching {
            val player = context.player() as? ServerPlayer ?: throw McpNoPlayerError()
            dispatchC2S(packet, player)
        }.getOrElse { e ->
            val error = if (e is McpError) {
                e
            } else {
                e.printStackTrace()
                McpInternalError(e.toString())
            }
            mcpErrorText(error)
        }
        context.reply(GameNetPayload211.fromS2C(McpS2CNetPacket(packet.reqId, resp.toString())))
    }

    private fun dispatchC2S(packet: McpC2SNetPacket, player: ServerPlayer): Any {
        val handler = C2S_HANDLERS[packet.className] ?: throw McpBadRequestError("unimplemented on server")
        return handler(packet.reqJson, player)
    }

    private inline fun <reified Q : Any> c2sHandler(
        noinline handle: (Q, ServerPlayer) -> Any,
    ): Pair<String, (String, ServerPlayer) -> Any> {
        return Q::class.java.name to { reqJson, player ->
            handle(decodeReq<Q>(reqJson), player)
        }
    }

    private inline fun <reified T> decodeReq(reqJson: String): T {
        return runCatching {
            json.decodeFromString<T>(reqJson)
        }.getOrElse {
            throw McpBadRequestError("malformed request")
        }
    }

    private fun mcpErrorText(e: McpError): String {
        return "${e.javaClass.simpleName.removeSuffix("Error")} ${e.detail}"
    }
}
