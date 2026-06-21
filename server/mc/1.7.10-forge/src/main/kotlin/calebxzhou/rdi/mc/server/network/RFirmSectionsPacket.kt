package calebxzhou.rdi.mc.server.network

import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import io.netty.buffer.ByteBuf

class RFirmSectionsPacket : IMessage {
    private var entries: List<Entry> = emptyList()

    constructor()

    constructor(entries: List<Entry>) {
        this.entries = entries
    }

    override fun fromBytes(buf: ByteBuf) {
        val count = buf.readInt().coerceIn(0, MAX_ENTRIES)
        val readEntries = ArrayList<Entry>(count)
        repeat(count) {
            val dimensionId = ByteBufUtils.readUTF8String(buf)
            val chunkX = buf.readInt()
            val sectionY = buf.readInt()
            val chunkZ = buf.readInt()
            if (dimensionId.length <= MAX_DIMENSION_ID_LENGTH) {
                readEntries += Entry(dimensionId, chunkX, sectionY, chunkZ)
            }
        }
        entries = readEntries
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entries.size)
        for (entry in entries) {
            ByteBufUtils.writeUTF8String(buf, entry.dimensionId)
            buf.writeInt(entry.chunkX)
            buf.writeInt(entry.sectionY)
            buf.writeInt(entry.chunkZ)
        }
    }

    class Handler : IMessageHandler<RFirmSectionsPacket, IMessage> {
        override fun onMessage(message: RFirmSectionsPacket, ctx: MessageContext): IMessage? = null
    }

    data class Entry(
        val dimensionId: String,
        val chunkX: Int,
        val sectionY: Int,
        val chunkZ: Int,
    )

    companion object {
        private const val MAX_ENTRIES = 4096
        private const val MAX_DIMENSION_ID_LENGTH = 512
    }
}
