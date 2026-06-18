package calebxzhou.rdi.mc.server.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

@JvmRecord
data class RGlobalPlayerListPayload(val json: String) : CustomPacketPayload {
    constructor(buf: RegistryFriendlyByteBuf) : this(buf.readUtf(MAX_JSON_LENGTH))

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(json, MAX_JSON_LENGTH)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_JSON_LENGTH = 262144
        val TYPE: CustomPacketPayload.Type<RGlobalPlayerListPayload> =
            CustomPacketPayload.Type(
                ResourceLocation.fromNamespaceAndPath("rdi", "global_player_list")
            )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RGlobalPlayerListPayload> =
            CustomPacketPayload.codec(RGlobalPlayerListPayload::write, ::RGlobalPlayerListPayload)
    }
}
