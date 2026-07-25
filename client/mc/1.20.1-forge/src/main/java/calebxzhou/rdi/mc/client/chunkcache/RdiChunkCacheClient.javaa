package calebxzhou.rdi.mc.client.chunkcache;

import calebxzhou.rdi.mc.chunkcache.RChunkCacheManifestPacket;
import calebxzhou.rdi.mc.chunkcache.RdiChunkCacheConfig;
import calebxzhou.rdi.mc.client.network.RClientNetwork;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class RdiChunkCacheClient {
    private static final Logger LOGGER = LogManager.getLogger("rdi-chunk-cache");
    private static final int RESEND_THRESHOLD = 64;

    @Nullable
    private static RdiChunkCacheDatabase db;
    private static LongSet cachedHashes = new LongOpenHashSet();
    private static int pendingNewChunks;
    private static long generation;

    private RdiChunkCacheClient() {
    }

    public static synchronized void open(Path gameDir, String serverKey) {
        close();
        if (!RdiChunkCacheConfig.ENABLED) {
            return;
        }

        try {
            Path cacheDir = gameDir.resolve("rdi_chunk_cache").resolve(addressHash(serverKey));
            Files.createDirectories(cacheDir);
            long maxBytes = (long) RdiChunkCacheConfig.MAX_SIZE_MB * 1024L * 1024L;
            db = new RdiChunkCacheDatabase(cacheDir, maxBytes);
            rebuildHashIndex();
            LOGGER.info("RDI chunk cache opened for {}", serverKey);
        } catch (IOException e) {
            LOGGER.error("open RDI chunk cache failed", e);
            db = null;
            cachedHashes = new LongOpenHashSet();
        }
    }

    public static synchronized void close() {
        generation++;
        if (db != null) {
            db.close();
            db = null;
        }
        cachedHashes = new LongOpenHashSet();
        pendingNewChunks = 0;
    }

    public static synchronized boolean isOpen() {
        return db != null;
    }

    public static synchronized long generation() {
        return generation;
    }

    public static synchronized void advanceGeneration() {
        generation++;
    }

    public static synchronized boolean isGeneration(long expectedGeneration) {
        return generation == expectedGeneration && db != null;
    }

    @Nullable
    public static synchronized byte[] get(long expectedGeneration, long hash) {
        return generation == expectedGeneration && db != null ? db.get(hash) : null;
    }

    public static synchronized void delete(long expectedGeneration, long hash) {
        if (generation == expectedGeneration && db != null) {
            db.delete(hash);
            cachedHashes.remove(hash);
        }
    }

    public static synchronized void put(long expectedGeneration, long hash, byte[] data) {
        if (generation != expectedGeneration || db == null) {
            return;
        }
        db.put(hash, data);
        if (cachedHashes.add(hash)) {
            pendingNewChunks++;
        }
    }

    public static synchronized boolean drainAndShouldResend(long expectedGeneration) {
        if (generation != expectedGeneration || pendingNewChunks < RESEND_THRESHOLD) {
            return false;
        }
        pendingNewChunks = 0;
        return true;
    }

    public static synchronized void evictAndRebuildIfNeeded(long expectedGeneration) {
        if (generation == expectedGeneration && db != null && db.evictIfNeeded()) {
            rebuildHashIndex();
        }
    }

    public static synchronized byte[] manifestBytes() {
        int maxHashes = (RChunkCacheManifestPacket.MAX_TOTAL_SIZE - Integer.BYTES) / Long.BYTES;
        if (cachedHashes.size() > maxHashes) {
            LOGGER.warn("chunk cache manifest has {} entries, disabling cache for this connection", cachedHashes.size());
            return ByteBuffer.allocate(Integer.BYTES).putInt(0).array();
        }

        long[] hashes = cachedHashes.toLongArray();
        Arrays.sort(hashes);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + hashes.length * Long.BYTES);
        buffer.putInt(hashes.length);
        for (long hash : hashes) {
            buffer.putLong(hash);
        }
        return buffer.array();
    }

    public static void sendManifestAndReady() {
        byte[] manifest = manifestBytes();
        int offset = 0;
        while (offset < manifest.length) {
            int chunkSize = Math.min(RChunkCacheManifestPacket.MAX_CHUNK_SIZE, manifest.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(manifest, offset, chunk, 0, chunkSize);
            offset += chunkSize;
            RClientNetwork.INSTANCE.sendChunkCacheManifest(chunk, offset < manifest.length);
        }
        RClientNetwork.INSTANCE.sendChunkCacheReady();
    }

    public static void sendManifestAndReady(long expectedGeneration) {
        if (isGeneration(expectedGeneration)) {
            sendManifestAndReady();
        }
    }

    private static void rebuildHashIndex() {
        if (db == null) {
            cachedHashes = new LongOpenHashSet();
            return;
        }
        cachedHashes = db.getAllHashes();
    }

    private static String addressHash(String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(address.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
