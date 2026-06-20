package calebxzhou.rdi.mc.server.network

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import io.netty.buffer.ByteBuf

class RGlobalPlayerListPacket : IMessage {
    private var json = ""

    constructor()

    constructor(json: String) {
        this.json = json
    }

    override fun fromBytes(buf: ByteBuf) {
        json = ByteBufUtils.readUTF8String(buf)
        if (json.length > MAX_JSON_LENGTH) {
            json = ""
        }
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, json)
    }

    class Handler : IMessageHandler<RGlobalPlayerListPacket, IMessage> {
        override fun onMessage(message: RGlobalPlayerListPacket, ctx: MessageContext): IMessage? {
            return null
        }
    }

    companion object {
        private const val MAX_JSON_LENGTH = 262144
    }
}
