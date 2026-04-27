package calebxzhou.rdi.mc.server.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RdiGlobalPlayerListPayload(String json) implements CustomPacketPayload {
    private static final int MAX_JSON_LENGTH = 262_144;
    public static final Type<RdiGlobalPlayerListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("rdi", "global_player_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RdiGlobalPlayerListPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RdiGlobalPlayerListPayload::write, RdiGlobalPlayerListPayload::new);

    public RdiGlobalPlayerListPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(MAX_JSON_LENGTH));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(json, MAX_JSON_LENGTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
