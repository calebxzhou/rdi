package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

class RFirmSectionsPacket : IMessage {
    private val entries = mutableListOf<Entry>()

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
        entries.forEach {
            ByteBufUtils.writeUTF8String(buf, it.dimensionId)
            buf.writeInt(it.chunkX)
            buf.writeInt(it.sectionY)
            buf.writeInt(it.chunkZ)
        }
    }

    class Handler : IMessageHandler<RFirmSectionsPacket, IMessage?> {
        override fun onMessage(message: RFirmSectionsPacket, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                val sectionsByDimension = linkedMapOf<String, MutableList<SectionPos>>()
                message.entries.forEach {
                    sectionsByDimension.getOrPut(it.dimensionId) { mutableListOf() }
                        .add(SectionPos(it.chunkX, it.sectionY, it.chunkZ))
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
        val chunkZ: Int
    )

    companion object {
        private const val MAX_ENTRIES = 65536
        private const val MAX_DIMENSION_ID_LENGTH = 512
    }
}
