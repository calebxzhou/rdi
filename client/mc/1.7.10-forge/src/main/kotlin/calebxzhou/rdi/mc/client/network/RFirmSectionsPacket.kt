package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import cpw.mods.fml.common.network.ByteBufUtils
import cpw.mods.fml.common.network.simpleimpl.IMessage
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler
import cpw.mods.fml.common.network.simpleimpl.MessageContext
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft

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
            val dimensionId = ByteBufUtils.readUTF8String(buf)
            val chunkX = buf.readInt()
            val sectionY = buf.readInt()
            val chunkZ = buf.readInt()
            if (dimensionId.length <= MAX_DIMENSION_ID_LENGTH) {
                entries += Entry(dimensionId, chunkX, sectionY, chunkZ)
            }
        }
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
        override fun onMessage(message: RFirmSectionsPacket, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().func_152344_a {
                val sectionsByDimension = linkedMapOf<String, MutableList<SectionPos>>()
                for (entry in message.entries) {
                    sectionsByDimension.getOrPut(entry.dimensionId) { mutableListOf() }
                        .add(SectionPos(entry.chunkX, entry.sectionY, entry.chunkZ))
                }
                RDI.FIRM_CHUNKS = sectionsByDimension
            }
            return null
        }
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
