package calebxzhou.rdi.mc.chunkcache;

import net.minecraft.network.FriendlyByteBuf;

public record RChunkCacheManifestPacket(byte[] manifestBytes, boolean hasMore) {
    public static final int MAX_CHUNK_SIZE = 30_000;
    public static final int MAX_TOTAL_SIZE = 2 * 1024 * 1024;

    public static void encode(RChunkCacheManifestPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.hasMore);
        buf.writeByteArray(packet.manifestBytes);
    }

    public static RChunkCacheManifestPacket decode(FriendlyByteBuf buf) {
        boolean hasMore = buf.readBoolean();
        return new RChunkCacheManifestPacket(buf.readByteArray(MAX_CHUNK_SIZE), hasMore);
    }
}
