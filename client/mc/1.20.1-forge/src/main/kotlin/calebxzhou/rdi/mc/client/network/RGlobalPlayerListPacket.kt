package calebxzhou.rdi.mc.client.network

import calebxzhou.rdi.mc.common.RGlobalPlayerList
import com.google.gson.Gson
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

@JvmRecord
data class RGlobalPlayerListPacket(val json: String) {
    companion object {
        private const val MAX_JSON_LENGTH = 262144
        private val GSON = Gson()

        fun encode(packet: RGlobalPlayerListPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.json, MAX_JSON_LENGTH)
        }

        fun decode(buf: FriendlyByteBuf) = RGlobalPlayerListPacket(buf.readUtf(MAX_JSON_LENGTH))

        fun handle(packet: RGlobalPlayerListPacket, contextSupplier: Supplier<NetworkEvent.Context>) {
            val context = contextSupplier.get()
            context.enqueueWork {
                GlobalPlayerListState.update(
                    GSON.fromJson(packet.json, RGlobalPlayerList::class.java)
                )
            }
            context.packetHandled = true
        }
    }
}
