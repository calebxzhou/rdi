package calebxzhou.rdi.mc.client.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record RFirmSectionsPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_DIMENSION_ID_LENGTH = 512;
    public static final Type<RFirmSectionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("rdi", "firm_sections"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RFirmSectionsPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RFirmSectionsPayload::write, RFirmSectionsPayload::new);

    public RFirmSectionsPayload {
        entries = List.copyOf(entries);
    }

    public RFirmSectionsPayload(RegistryFriendlyByteBuf buf) {
        this(readEntries(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (var entry : entries) {
            buf.writeUtf(entry.dimensionId(), MAX_DIMENSION_ID_LENGTH);
            buf.writeInt(entry.chunkX());
            buf.writeInt(entry.sectionY());
            buf.writeInt(entry.chunkZ());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buf) {
        var size = buf.readVarInt();
        var entries = new ArrayList<Entry>(size);
        for (var i = 0; i < size; i++) {
            entries.add(new Entry(
                    buf.readUtf(MAX_DIMENSION_ID_LENGTH),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            ));
        }
        return entries;
    }

    public record Entry(String dimensionId, int chunkX, int sectionY, int chunkZ) {
    }
}
