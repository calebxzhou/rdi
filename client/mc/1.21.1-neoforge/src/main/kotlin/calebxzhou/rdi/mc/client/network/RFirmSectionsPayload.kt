package calebxzhou.rdi.mc.client.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

class RFirmSectionsPayload(entries: List<Entry>) : CustomPacketPayload {
    constructor(buf: RegistryFriendlyByteBuf) : this(readEntries(buf))

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeVarInt(entries.size)
        for (entry in entries) {
            buf.writeUtf(entry.dimensionId, MAX_DIMENSION_ID_LENGTH)
            buf.writeInt(entry.chunkX)
            buf.writeInt(entry.sectionY)
            buf.writeInt(entry.chunkZ)
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    @JvmRecord
    data class Entry(val dimensionId: String, val chunkX: Int, val sectionY: Int, val chunkZ: Int)

    val entries: List<Entry> = entries.toList()

    companion object {
        private const val MAX_DIMENSION_ID_LENGTH = 512
        val TYPE: CustomPacketPayload.Type<RFirmSectionsPayload> = CustomPacketPayload.Type(
            ResourceLocation.fromNamespaceAndPath("rdi", "firm_sections")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RFirmSectionsPayload> =
            CustomPacketPayload.codec(RFirmSectionsPayload::write, ::RFirmSectionsPayload)

        private fun readEntries(buf: RegistryFriendlyByteBuf): List<Entry> {
            val size = buf.readVarInt()
            val entries = ArrayList<Entry>(size)
            for (i in 0..<size) {
                entries.add(
                    Entry(
                        buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                        buf.readInt(),
                        buf.readInt(),
                        buf.readInt()
                    )
                )
            }
            return entries
        }
    }
}
