package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common.SectionPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

@JvmRecord
data class RFirmSectionsPacket(val entries: List<Entry>) {
    @JvmRecord
    data class Entry(val dimensionId: String, val chunkX: Int, val sectionY: Int, val chunkZ: Int)

    companion object {
        private const val MAX_DIMENSION_ID_LENGTH = 512
        private const val MAX_ENTRIES = 65536

        fun encode(packet: RFirmSectionsPacket, buf: FriendlyByteBuf) {
            buf.writeVarInt(packet.entries.size)
            for (entry in packet.entries) {
                buf.writeUtf(entry.dimensionId, MAX_DIMENSION_ID_LENGTH)
                buf.writeInt(entry.chunkX)
                buf.writeInt(entry.sectionY)
                buf.writeInt(entry.chunkZ)
            }
        }

        fun decode(buf: FriendlyByteBuf): RFirmSectionsPacket {
            val count = buf.readVarInt()
            require(count in 0..MAX_ENTRIES) { "Invalid firm section count: $count" }
            return RFirmSectionsPacket(
                List(count) {
                    Entry(
                        dimensionId = buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                        chunkX = buf.readInt(),
                        sectionY = buf.readInt(),
                        chunkZ = buf.readInt()
                    )
                }
                )

        }

        fun handle(packet: RFirmSectionsPacket, contextSupplier: Supplier<NetworkEvent.Context>) {
            val context = contextSupplier.get()
            context.enqueueWork {
                val sectionsByDimension = HashMap<String, MutableList<SectionPos>>()
                for (entry in packet.entries) {
                    sectionsByDimension.getOrPut(entry.dimensionId, ::ArrayList)
                        .add(SectionPos(entry.chunkX, entry.sectionY, entry.chunkZ))
                }
                RDI.FIRM_CHUNKS = sectionsByDimension
            }
            context.packetHandled = true
        }
    }
}
