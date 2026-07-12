package calebxzhou.rdi.mc.chunkcache;

import net.minecraft.network.FriendlyByteBuf;

public record RChunkHashPacket(int chunkX, int chunkZ, long contentHash, long requestId) {
    public static void encode(RChunkHashPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.chunkX);
        buf.writeInt(packet.chunkZ);
        buf.writeLong(packet.contentHash);
        buf.writeLong(packet.requestId);
    }

    public static RChunkHashPacket decode(FriendlyByteBuf buf) {
        return new RChunkHashPacket(buf.readInt(), buf.readInt(), buf.readLong(), buf.readLong());
    }
}
