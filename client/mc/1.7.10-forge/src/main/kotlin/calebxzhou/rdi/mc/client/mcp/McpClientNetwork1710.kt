package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import cpw.mods.fml.common.network.NetworkRegistry
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
import cpw.mods.fml.relauncher.Side
import io.netty.buffer.ByteBuf
import kotlinx.serialization.encodeToString
import net.minecraft.client.Minecraft
import kotlin.concurrent.Volatile

private const val MAX_MCP_JSON_BYTES = 2_097_152

object McpClientNetwork1710 {
    val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel("rdi_mcp")

    @Volatile
    private var registered = false

    @JvmStatic
    fun register() {
        if (registered) {
            return
        }
        registered = true
        CHANNEL.registerMessage(
            GameNetPayload1710.Handler::class.java,
            GameNetPayload1710::class.java,
            0,
            Side.CLIENT
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
            Minecraft.getMinecraft().func_152344_a {
                McpGameImpl1710.complete(message.s2cPacket())
            }
            return null
        }
    }

    companion object {
        fun fromC2S(packet: McpC2SNetPacket) = GameNetPayload1710(json.encodeToString(packet))

        fun fromS2C(packet: McpS2CNetPacket) = GameNetPayload1710(json.encodeToString(packet))
    }
}
