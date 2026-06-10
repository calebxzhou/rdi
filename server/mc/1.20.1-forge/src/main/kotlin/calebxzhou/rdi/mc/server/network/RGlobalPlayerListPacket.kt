package calebxzhou.rdi.mc.server.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class RGlobalPlayerListPacket(val json: String) {
    companion object {
        private const val MAX_JSON_LENGTH = 262144

        @JvmStatic
        fun encode(packet: RGlobalPlayerListPacket, buf: FriendlyByteBuf) {
            buf.writeUtf(packet.json, MAX_JSON_LENGTH)
        }

        @JvmStatic
        fun decode(buf: FriendlyByteBuf): RGlobalPlayerListPacket {
            return RGlobalPlayerListPacket(buf.readUtf(MAX_JSON_LENGTH))
        }

        fun handle(packet: RGlobalPlayerListPacket?, context: Supplier<NetworkEvent.Context?>) {
            context.get()!!.setPacketHandled(true)
        }
    }
}
