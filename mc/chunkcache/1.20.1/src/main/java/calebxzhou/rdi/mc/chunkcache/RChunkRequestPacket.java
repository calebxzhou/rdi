package calebxzhou.rdi.mc.chunkcache;

import net.minecraft.network.FriendlyByteBuf;

public record RChunkRequestPacket(int chunkX, int chunkZ, long contentHash, long requestId) {
    public static void encode(RChunkRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.chunkX);
        buf.writeInt(packet.chunkZ);
        buf.writeLong(packet.contentHash);
        buf.writeLong(packet.requestId);
    }

    public static RChunkRequestPacket decode(FriendlyByteBuf buf) {
        return new RChunkRequestPacket(buf.readInt(), buf.readInt(), buf.readLong(), buf.readLong());
    }
}
