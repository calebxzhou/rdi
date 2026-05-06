package calebxzhou.rdi.mc.server.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RMcpPayload(String requestId, String kind, String action, String code, String json) implements CustomPacketPayload {
    private static final int MAX_JSON_LENGTH = 2_097_152;
    private static final int MAX_TEXT_LENGTH = 128;
    public static final Type<RMcpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rdi", "mcp"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RMcpPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RMcpPayload::write, RMcpPayload::new);

    public RMcpPayload(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(MAX_TEXT_LENGTH),
                buf.readUtf(MAX_TEXT_LENGTH),
                buf.readUtf(MAX_TEXT_LENGTH),
                buf.readUtf(MAX_TEXT_LENGTH),
                buf.readUtf(MAX_JSON_LENGTH)
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(requestId, MAX_TEXT_LENGTH);
        buf.writeUtf(kind, MAX_TEXT_LENGTH);
        buf.writeUtf(action, MAX_TEXT_LENGTH);
        buf.writeUtf(code, MAX_TEXT_LENGTH);
        buf.writeUtf(json, MAX_JSON_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
