package calebxzhou.rdi.mc.server.mcpimpl

import calebxzhou.rdi.mc.common2.mcp.*
import calebxzhou.rdi.mc.common2.mcp.model.*
import calebxzhou.rdi.mc.server.mcpimpl.handler.BlockHandler
import calebxzhou.rdi.mc.server.mcpimpl.handler.ContainerHandler
import calebxzhou.rdi.mc.server.mcpimpl.handler.CraftHandler
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import kotlin.concurrent.Volatile
import kotlin.getOrThrow

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
    }
}

data class McpS2CPacket(val packetJson: String) {
    companion object {
        @JvmStatic
        fun encode(packet: McpS2CPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.packetJson, MAX_JSON_LENGTH)
        }

        @JvmStatic
        fun decode(buf: FriendlyByteBuf): McpS2CPacket {
            return McpS2CPacket(buf.readUtf(MAX_JSON_LENGTH))
        }

        @JvmStatic
        fun fromS2C(packet: McpS2CNetPacket): McpS2CPacket {
            return McpS2CPacket(json.encodeToString(packet))
        }
    }
}

object McpNetwork {
    private const val PROTOCOL_VERSION = "1"

    @Volatile
    private var registered = false

    private val C2S_HANDLERS: Map<String, (String, ServerPlayer) -> Any> = mapOf(
        c2sHandler<BlockBreakBoxQ> { req, player -> BlockHandler.breakBox(req, player).getOrThrow() },
        c2sHandler<BlockBreakDiscreteQ> { req, player -> BlockHandler.breakDiscrete(req, player).getOrThrow() },
        c2sHandler<BlockPlaceBoxQ> { req, player -> BlockHandler.handleBox(req, player).getOrThrow() },
        c2sHandler<BlockPlaceDiscreteQ> { req, player -> BlockHandler.handleDiscrete(req, player).getOrThrow() },
        c2sHandler<BlockHarvestResultQ> { req, player -> BlockHandler.harvestResult(req, player).getOrThrow() },
        c2sHandler<BlockUseItemQ> { req, player -> BlockHandler.useItemOn(req, player).getOrThrow() },
        c2sHandler<ContainerDropItemQ> { req, player -> ContainerHandler.dropItem(req, player).getOrThrow() },
        c2sHandler<ContainerSlotListQ> { req, player -> ContainerHandler.slotList(req, player).getOrThrow() },
        c2sHandler<ContainerMoveQ> { req, player -> ContainerHandler.move(req, player).getOrThrow() },
        c2sHandler<CraftQ> { req, player -> CraftHandler.craft(req, player).getOrThrow() },
    )

    private val CHANNEL: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath("rdi", "game"),
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
            .consumerMainThread { packet, context ->
                val ctx = context.get()
                handleC2S(packet.c2sPacket(), ctx.sender)
                ctx.packetHandled = true
            }
            .add()
        CHANNEL.messageBuilder(McpS2CPacket::class.java, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(McpS2CPacket::encode)
            .decoder(McpS2CPacket::decode)
            .consumerMainThread { _, context -> context.get().packetHandled = true }
            .add()
    }

    private fun handleC2S(packet: McpC2SNetPacket, player: ServerPlayer?) {
        val resp = runCatching {
            dispatchC2S(packet, player ?: throw McpNoPlayerError())
        }.getOrElse { e ->
            val error = if (e is McpError) {
                e
            } else {
                e.printStackTrace()
                McpInternalError(e.toString())
            }
            "${error.javaClass.simpleName} ${error.detail}"
        }
        if (player != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with { player }, McpS2CPacket.fromS2C(McpS2CNetPacket(packet.reqId, resp.toString())))
        }
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
            it.printStackTrace()
            throw McpBadRequestError("C2S request decode Error ${it.message}")
        }
    }
}
