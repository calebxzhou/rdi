package calebxzhou.rdi.mc.client.chunkcache;

import calebxzhou.rdi.mc.chunkcache.ChunkHash201;
import calebxzhou.rdi.mc.chunkcache.RChunkHashPacket;
import calebxzhou.rdi.mc.client.mixin.AClientPacketListener;
import calebxzhou.rdi.mc.client.network.RClientNetwork;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RdiChunkCacheClientHandler {
    private static final class AwaitingChunk {
        private final long generation;
        private final ClientPacketListener listener;
        private final ResourceKey<Level> dimension;
        private final List<Runnable> deferredPackets = new ArrayList<>();
        private boolean requestedFullChunk;

        private AwaitingChunk(long generation, ClientPacketListener listener, ResourceKey<Level> dimension) {
            this.generation = generation;
            this.listener = listener;
            this.dimension = dimension;
        }
    }

    private static final Logger LOGGER = LogManager.getLogger("rdi-chunk-cache");
    private static final ExecutorService CACHE_READ_WORKER = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("RDI-ChunkCacheRead-%d").setDaemon(true).build());
    private static final ExecutorService CACHE_WRITE_WORKER = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("RDI-ChunkCacheWrite-%d").setDaemon(true).build());
    private static final Map<Long, AwaitingChunk> AWAITING_CHUNKS = new HashMap<>();

    private RdiChunkCacheClientHandler() {
    }

    public static void cachePacket(ClientboundLevelChunkWithLightPacket packet) {
        if (!RdiChunkCacheClient.isOpen()) {
            completeAwaiting(packet.getX(), packet.getZ());
            return;
        }

        ClientboundLevelChunkPacketData chunkData = packet.getChunkData();
        ClientboundLightUpdatePacketData lightData = packet.getLightData();
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();
        long generation = RdiChunkCacheClient.generation();
        CACHE_WRITE_WORKER.execute(() -> {
            if (!RdiChunkCacheClient.isGeneration(generation)) {
                return;
            }
            long hash = ChunkHash201.compute(chunkData, chunkX, chunkZ).hash();
            if (RdiChunkCacheClient.get(generation, hash) != null) {
                return;
            }
            RdiChunkCacheClient.put(generation, hash, serialize(chunkData, lightData));

            if (RdiChunkCacheClient.drainAndShouldResend(generation)) {
                RdiChunkCacheClient.evictAndRebuildIfNeeded(generation);
                Minecraft.getInstance().execute(() -> RdiChunkCacheClient.sendManifestAndReady(generation));
            }
        });
        completeAwaiting(chunkX, chunkZ);
    }

    public static void handleChunkHash(RChunkHashPacket packet) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getLevel() == null) {
            return;
        }
        long generation = RdiChunkCacheClient.generation();
        ResourceKey<Level> dimension = listener.getLevel().dimension();
        if (!beginAwaiting(packet.chunkX(), packet.chunkZ(), generation, listener, dimension)) {
            return;
        }
        CACHE_READ_WORKER.execute(() -> loadCachedChunk(packet, generation, listener, dimension));
    }

    private static void applyCachedPacket(
            int chunkX,
            int chunkZ,
            ClientboundLevelChunkPacketData chunkData,
            ClientboundLightUpdatePacketData lightData
    ) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            return;
        }
        ClientLevel level = listener.getLevel();
        if (level == null) {
            return;
        }
        LOGGER.info("cache hit for {} {}",chunkX,chunkZ);
        AClientPacketListener accessor = (AClientPacketListener) listener;
        accessor.rdi$updateLevelChunk(chunkX, chunkZ, chunkData);
        level.queueLightUpdate(() -> {
            accessor.rdi$applyLightData(chunkX, chunkZ, lightData);
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
            if (chunk != null) {
                accessor.rdi$enableChunkLight(chunk, chunkX, chunkZ);
            }
        });
    }

    public static boolean deferIfAwaiting(int chunkX, int chunkZ, Runnable packetHandler) {
        AwaitingChunk awaiting = AWAITING_CHUNKS.get(chunkKey(chunkX, chunkZ));
        if (awaiting == null) {
            return false;
        }
        awaiting.deferredPackets.add(packetHandler);
        return true;
    }

    public static boolean isAwaiting(int chunkX, int chunkZ) {
        return AWAITING_CHUNKS.containsKey(chunkKey(chunkX, chunkZ));
    }

    public static void clearDeferredPackets() {
        AWAITING_CHUNKS.clear();
    }

    private static boolean beginAwaiting(
            int chunkX,
            int chunkZ,
            long generation,
            ClientPacketListener listener,
            ResourceKey<Level> dimension
    ) {
        long key = chunkKey(chunkX, chunkZ);
        if (AWAITING_CHUNKS.containsKey(key)) {
            return false;
        }
        AWAITING_CHUNKS.put(key, new AwaitingChunk(generation, listener, dimension));
        return true;
    }

    private static void loadCachedChunk(
            RChunkHashPacket packet,
            long generation,
            ClientPacketListener listener,
            ResourceKey<Level> dimension
    ) {
        try {
            byte[] cachedBytes = RdiChunkCacheClient.get(generation, packet.contentHash());
            if (cachedBytes == null) {
                Minecraft.getInstance().execute(() -> requestFullChunk(packet, generation, listener, dimension));
                return;
            }

            ClientboundLevelChunkPacketData chunkData;
            ClientboundLightUpdatePacketData lightData;
            var inner = Unpooled.wrappedBuffer(cachedBytes);
            try {
                FriendlyByteBuf buf = new FriendlyByteBuf(inner);
                chunkData = new ClientboundLevelChunkPacketData(buf, packet.chunkX(), packet.chunkZ());
                lightData = new ClientboundLightUpdatePacketData(buf, packet.chunkX(), packet.chunkZ());
            } finally {
                inner.release();
            }

            Minecraft.getInstance().execute(() -> {
                if (!isAwaitingContext(packet.chunkX(), packet.chunkZ(), generation, listener, dimension)) {
                    discardAwaiting(packet.chunkX(), packet.chunkZ(), generation);
                    return;
                }
                applyCachedPacket(packet.chunkX(), packet.chunkZ(), chunkData, lightData);
                completeAwaiting(packet.chunkX(), packet.chunkZ());
            });
        } catch (Exception e) {
            RdiChunkCacheClient.delete(generation, packet.contentHash());
            Minecraft.getInstance().execute(() -> requestFullChunk(packet, generation, listener, dimension));
            LOGGER.error("read cached chunk failed {},{}", packet.chunkX(), packet.chunkZ(), e);
        }
    }

    private static void requestFullChunk(
            RChunkHashPacket packet,
            long generation,
            ClientPacketListener listener,
            ResourceKey<Level> dimension
    ) {
        if (!isCurrentContext(generation, listener, dimension)) {
            discardAwaiting(packet.chunkX(), packet.chunkZ(), generation);
            return;
        }
        long key = chunkKey(packet.chunkX(), packet.chunkZ());
        AwaitingChunk awaiting = AWAITING_CHUNKS.get(key);
        if (awaiting == null
                || awaiting.generation != generation
                || awaiting.listener != listener
                || !awaiting.dimension.equals(dimension)
                || awaiting.requestedFullChunk) {
            return;
        }
        awaiting.requestedFullChunk = true;
        LOGGER.info("requesting chunk {} {}", packet.chunkX(), packet.chunkZ());
        RClientNetwork.INSTANCE.sendChunkRequest(
                packet.chunkX(),
                packet.chunkZ(),
                packet.contentHash(),
                packet.requestId()
        );
    }

    private static void completeAwaiting(int chunkX, int chunkZ) {
        AwaitingChunk awaiting = AWAITING_CHUNKS.remove(chunkKey(chunkX, chunkZ));
        if (awaiting == null) {
            return;
        }
        for (Runnable packet : awaiting.deferredPackets) {
            packet.run();
        }
    }

    private static void discardAwaiting(int chunkX, int chunkZ, long generation) {
        long key = chunkKey(chunkX, chunkZ);
        AwaitingChunk awaiting = AWAITING_CHUNKS.get(key);
        if (awaiting != null && awaiting.generation == generation) {
            AWAITING_CHUNKS.remove(key);
        }
    }

    private static boolean isCurrentContext(
            long generation,
            ClientPacketListener listener,
            ResourceKey<Level> dimension
    ) {
        if (!RdiChunkCacheClient.isGeneration(generation) || Minecraft.getInstance().getConnection() != listener) {
            return false;
        }
        ClientLevel level = listener.getLevel();
        return level != null && level.dimension().equals(dimension);
    }

    private static boolean isAwaitingContext(
            int chunkX,
            int chunkZ,
            long generation,
            ClientPacketListener listener,
            ResourceKey<Level> dimension
    ) {
        if (!isCurrentContext(generation, listener, dimension)) {
            return false;
        }
        AwaitingChunk awaiting = AWAITING_CHUNKS.get(chunkKey(chunkX, chunkZ));
        return awaiting != null
                && awaiting.generation == generation
                && awaiting.listener == listener
                && awaiting.dimension.equals(dimension);
    }

    private static byte[] serialize(
            ClientboundLevelChunkPacketData chunkData,
            ClientboundLightUpdatePacketData lightData
    ) {
        var inner = Unpooled.buffer(8192);
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(inner);
            chunkData.write(buf);
            lightData.write(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            inner.release();
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX & 0xffffffffL | ((long) chunkZ & 0xffffffffL) << 32;
    }
}
