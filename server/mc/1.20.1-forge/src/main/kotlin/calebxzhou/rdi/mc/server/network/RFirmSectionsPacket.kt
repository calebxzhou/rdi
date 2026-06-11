package calebxzhou.rdi.mc.server.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class RFirmSectionsPacket(val entries: List<Entry>) {
    data class Entry(
        val dimensionId: String,
        val chunkX: Int,
        val sectionY: Int,
        val chunkZ: Int
    )

    companion object {
        private const val MAX_DIMENSION_ID_LENGTH = 512
        private const val MAX_ENTRIES = 65536

        @JvmStatic
        fun encode(packet: RFirmSectionsPacket, buf: FriendlyByteBuf) {
            buf.writeVarInt(packet.entries.size)
            packet.entries.forEach { entry ->
                buf.writeUtf(entry.dimensionId, MAX_DIMENSION_ID_LENGTH)
                buf.writeInt(entry.chunkX)
                buf.writeInt(entry.sectionY)
                buf.writeInt(entry.chunkZ)
            }
        }

        @JvmStatic
        fun decode(buf: FriendlyByteBuf): RFirmSectionsPacket {
            val count = buf.readVarInt()
            require(count in 0..MAX_ENTRIES) { "Invalid firm section count: $count" }
            return RFirmSectionsPacket(List(count) {
                Entry(
                    buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
                )
            })
        }

        @JvmStatic
        fun handle(packet: RFirmSectionsPacket, context: Supplier<NetworkEvent.Context>) {
            context.get().setPacketHandled(true)
        }
    }
}
