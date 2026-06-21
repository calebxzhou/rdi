package calebxzhou.rdi.mc.server.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpInternalError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockHarvestResultQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockUseItemQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerDropItemQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.CraftQ
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
import cpw.mods.fml.relauncher.Side
import io.netty.buffer.ByteBuf
import kotlinx.serialization.encodeToString
import net.minecraft.entity.player.EntityPlayerMP
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.Volatile

private const val MAX_MCP_JSON_BYTES = 2_097_152

object McpServerNetwork1710 {
    val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_mcp")

    @Volatile
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered) {
            return
        }
        registered = true
        FMLCommonHandler.instance().bus().register(McpServerTaskQueue1710)
        CHANNEL.registerMessage(
            GameNetPayload1710.Handler::class.java,
            GameNetPayload1710::class.java,
            0,
            Side.SERVER
        )
    }
}

class GameNetPayload1710 : IMessage {
    private var packetJson = ""

    constructor()

    constructor(packetJson: String) {
        this.packetJson = packetJson
    }

    override fun fromBytes(buf: ByteBuf) {
        val length = buf.readInt()
        require(length in 0..MAX_MCP_JSON_BYTES) { "invalid MCP payload length $length" }
        val bytes = ByteArray(length)
        buf.readBytes(bytes)
        packetJson = bytes.toString(Charsets.UTF_8)
    }

    override fun toBytes(buf: ByteBuf) {
        val bytes = packetJson.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_MCP_JSON_BYTES) { "MCP payload too large ${bytes.size}" }
        buf.writeInt(bytes.size)
        buf.writeBytes(bytes)
    }

    fun c2sPacket(): McpC2SNetPacket = json.decodeFromString(packetJson)

    fun s2cPacket(): McpS2CNetPacket = json.decodeFromString(packetJson)

    class Handler : IMessageHandler<GameNetPayload1710, IMessage> {
        override fun onMessage(message: GameNetPayload1710, ctx: MessageContext): IMessage? {
            val packet = message.c2sPacket()
            val player = ctx.serverHandler?.playerEntity as? EntityPlayerMP
            if (player != null) {
                McpServerTaskQueue1710.enqueue(packet, player)
            }
            return null
        }
    }

    companion object {
        fun fromC2S(packet: McpC2SNetPacket) = GameNetPayload1710(json.encodeToString(packet))

        fun fromS2C(packet: McpS2CNetPacket) = GameNetPayload1710(json.encodeToString(packet))
    }
}

object McpServerTaskQueue1710 {
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()

    fun enqueue(packet: McpC2SNetPacket, player: EntityPlayerMP) {
        tasks += {
            val response = McpGameDispatcher1710.handle(packet, player)
            McpServerNetwork1710.CHANNEL.sendTo(GameNetPayload1710.fromS2C(response), player)
        }
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }
        while (true) {
            val task = tasks.poll() ?: return
            task()
        }
    }
}

object McpGameDispatcher1710 {
    private val C2S_HANDLERS: Map<String, (String, EntityPlayerMP) -> Any> = mapOf(
        c2sHandler<BlockBreakBoxQ> { req, player ->
            BlockMcpHandler1710.breakBox(req, player).getOrThrow()
        },
        c2sHandler<BlockBreakDiscreteQ> { req, player ->
            BlockMcpHandler1710.breakDiscrete(req, player).getOrThrow()
        },
        c2sHandler<BlockPlaceBoxQ> { req, player ->
            BlockMcpHandler1710.placeBox(req, player).getOrThrow()
        },
        c2sHandler<BlockPlaceDiscreteQ> { req, player ->
            BlockMcpHandler1710.placeDiscrete(req, player).getOrThrow()
        },
        c2sHandler<BlockHarvestResultQ> { req, player ->
            BlockMcpHandler1710.harvestResult(req, player).getOrThrow()
        },
        c2sHandler<BlockUseItemQ> { req, player ->
            BlockMcpHandler1710.useItemOn(req, player).getOrThrow()
        },
        c2sHandler<ContainerSlotListQ> { req, player ->
            ContainerMcpHandler1710.slotList(req, player).getOrThrow()
        },
        c2sHandler<ContainerMoveQ> { req, player ->
            ContainerMcpHandler1710.move(req, player).getOrThrow()
        },
        c2sHandler<ContainerDropItemQ> { req, player ->
            ContainerMcpHandler1710.dropItem(req, player).getOrThrow()
        },
        c2sHandler<CraftQ> { req, player ->
            CraftMcpHandler1710.craft(req, player).getOrThrow()
        },
    )

    fun handle(packet: McpC2SNetPacket, player: EntityPlayerMP?): McpS2CNetPacket {
        val text = runCatching {
            dispatch(packet, player ?: throw McpNoPlayerError()).toString()
        }.getOrElse { e ->
            val error = if (e is McpError) {
                e
            } else {
                e.printStackTrace()
                McpInternalError(e.toString())
            }
            "${error.javaClass.simpleName} ${error.detail}"
        }
        return McpS2CNetPacket(packet.reqId, text)
    }

    private fun dispatch(packet: McpC2SNetPacket, player: EntityPlayerMP): Any {
        val handler = C2S_HANDLERS[packet.className] ?: throw McpBadRequestError("unimplemented on mc1.7.10 server")
        return handler(packet.reqJson, player)
    }

    private inline fun <reified Q : Any> c2sHandler(
        noinline handle: (Q, EntityPlayerMP) -> Any,
    ): Pair<String, (String, EntityPlayerMP) -> Any> {
        return Q::class.java.name to { reqJson, player ->
            handle(decodeReq<Q>(reqJson), player)
        }
    }

    private inline fun <reified T> decodeReq(reqJson: String): T {
        return runCatching {
            json.decodeFromString<T>(reqJson)
        }.getOrElse {
            throw McpBadRequestError("C2S request decode Error ${it.message}")
        }
    }
}
