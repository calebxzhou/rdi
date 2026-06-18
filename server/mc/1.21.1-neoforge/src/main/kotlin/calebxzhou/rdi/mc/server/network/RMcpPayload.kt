package calebxzhou.rdi.mc.server.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

@JvmRecord
data class RMcpPayload(
    val requestId: String,
    val kind: String,
    val action: String,
    val code: String,
    val json: String,
) : CustomPacketPayload {
    constructor(buf: RegistryFriendlyByteBuf) : this(
        buf.readUtf(MAX_TEXT_LENGTH),
        buf.readUtf(MAX_TEXT_LENGTH),
        buf.readUtf(MAX_TEXT_LENGTH),
        buf.readUtf(MAX_TEXT_LENGTH),
        buf.readUtf(MAX_JSON_LENGTH)
    )

    fun write(buf: RegistryFriendlyByteBuf) {
        buf.writeUtf(requestId, MAX_TEXT_LENGTH)
        buf.writeUtf(kind, MAX_TEXT_LENGTH)
        buf.writeUtf(action, MAX_TEXT_LENGTH)
        buf.writeUtf(code, MAX_TEXT_LENGTH)
        buf.writeUtf(json, MAX_JSON_LENGTH)
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_JSON_LENGTH = 2097152
        private const val MAX_TEXT_LENGTH = 128
        val TYPE: CustomPacketPayload.Type<RMcpPayload> =
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath("rdi", "mcp"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RMcpPayload> =
            CustomPacketPayload.codec(RMcpPayload::write, ::RMcpPayload)
    }
}
