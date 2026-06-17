package calebxzhou.rdi.mc.server.network

import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class RFirmSectionsPacket : IMessage {
    private val entries = mutableListOf<Entry>()

    constructor()

    constructor(entries: List<Entry>) {
        this.entries += entries
    }

    override fun fromBytes(buf: ByteBuf) {
        entries.clear()
        val count = buf.readInt().coerceIn(0, MAX_ENTRIES)
        repeat(count) {
            entries += Entry(
                ByteBufUtils.readUTF8String(buf),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
            )
        }
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entries.size)
        entries.forEach {
            ByteBufUtils.writeUTF8String(buf, it.dimensionId)
            buf.writeInt(it.chunkX)
            buf.writeInt(it.sectionY)
            buf.writeInt(it.chunkZ)
        }
    }

    class Handler : IMessageHandler<RFirmSectionsPacket, IMessage> {
        override fun onMessage(message: RFirmSectionsPacket, ctx: MessageContext): IMessage? = null
    }

    data class Entry(
        val dimensionId: String,
        val chunkX: Int,
        val sectionY: Int,
        val chunkZ: Int
    )

    companion object {
        private const val MAX_ENTRIES = 65536
    }
}
