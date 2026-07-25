package calebxzhou.rdi.mc.server.chunkcache;

import calebxzhou.rdi.mc.chunkcache.ChunkHash201;
import calebxzhou.rdi.mc.chunkcache.RChunkCacheManifestPacket;
import calebxzhou.rdi.mc.chunkcache.RChunkHashPacket;
import calebxzhou.rdi.mc.chunkcache.RChunkRequestPacket;
import calebxzhou.rdi.mc.chunkcache.RdiChunkCacheConfig;
import calebxzhou.rdi.mc.server.network.RServerNetwork;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RdiChunkCacheServer {
    private record PendingRequest(int chunkX, int chunkZ, long contentHash, long expiresAtMillis, ResourceKey<Level> dimension) {
    }

    private static final class RequestWindow {
        long startedAtMillis;
        int count;
    }

    private enum State {
        PENDING,
        READY,
        DISABLED
    }

    private static final Logger LOGGER = LogManager.getLogger("rdi-chunk-cache");
    private static final int REQUEST_TTL_MILLIS = 10_000;
    private static final int REQUESTS_PER_SECOND = 64;
    private static final int MAX_PENDING_REQUESTS = 4096;
    private static final Object LOCK = new Object();
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1);
    private static final Map<Connection, State> STATES = new WeakHashMap<>();
    private static final Map<Connection, LongSet> CACHED_HASHES = new WeakHashMap<>();
    private static final Map<Connection, ByteArrayOutputStream> MANIFESTS = new WeakHashMap<>();
    private static final Map<Connection, Map<Long, PendingRequest>> PENDING_REQUESTS = new WeakHashMap<>();
    private static final Map<Connection, RequestWindow> REQUEST_WINDOWS = new WeakHashMap<>();

    private RdiChunkCacheServer() {
    }

    public static boolean isReady(Connection connection) {
        synchronized (LOCK) {
            return STATES.get(connection) == State.READY;
        }
    }

    public static boolean shouldQueue(ServerPlayer player) {
        if (!RdiChunkCacheConfig.ENABLED) {
            return false;
        }
        Connection connection = player.connection.connection;
        synchronized (LOCK) {
            State state = STATES.get(connection);
            if (state == State.READY || state == State.DISABLED) {
                return false;
            }
            if (state == null) {
                STATES.put(connection, State.PENDING);
                schedulePendingTimeout(player);
            }
            return true;
        }
    }

    public static void acceptManifest(ServerPlayer player, RChunkCacheManifestPacket packet) {
        Connection connection = player.connection.connection;
        synchronized (LOCK) {
            if (STATES.get(connection) == State.DISABLED) {
                return;
            }
            ByteArrayOutputStream out = MANIFESTS.computeIfAbsent(connection, ignored -> new ByteArrayOutputStream());
            if (out.size() + packet.manifestBytes().length > RChunkCacheManifestPacket.MAX_TOTAL_SIZE) {
                MANIFESTS.remove(connection);
                CACHED_HASHES.remove(connection);
                LOGGER.warn("chunk cache manifest is too large from {}", player.getGameProfile().getName());
                return;
            }
            out.write(packet.manifestBytes(), 0, packet.manifestBytes().length);
            if (!packet.hasMore()) {
                byte[] bytes = out.toByteArray();
                MANIFESTS.remove(connection);
                LongSet hashes = readManifest(bytes);
                if (hashes == null) {
                    CACHED_HASHES.remove(connection);
                    LOGGER.warn("read chunk cache manifest failed from {}", player.getGameProfile().getName());
                } else {
                    CACHED_HASHES.put(connection, hashes);
                }
            }
        }
    }

    public static void markReady(ServerPlayer player) {
        Connection connection = player.connection.connection;
        synchronized (LOCK) {
            if (STATES.get(connection) == State.DISABLED) {
                return;
            }
            STATES.put(connection, State.READY);
        }
        RdiPendingChunkQueue.drain(connection);
    }

    public static boolean sendHashIfPossible(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet) {
        Connection connection = player.connection.connection;
        if (!isReady(connection)) {
            return false;
        }

        ChunkHash201.Result result = ChunkHash201.compute(packet.getChunkData(), packet.getX(), packet.getZ());
        LongSet hashes;
        long requestId;
        synchronized (LOCK) {
            hashes = CACHED_HASHES.get(connection);
            if (hashes == null || !hashes.contains(result.hash())) {
                return false;
            }
            requestId = registerPendingRequest(connection, player, packet.getX(), packet.getZ(), result.hash());
            if (requestId == 0L) {
                return false;
            }
        }
        RServerNetwork.INSTANCE.sendChunkHash(player, new RChunkHashPacket(packet.getX(), packet.getZ(), result.hash(), requestId));
        return true;
    }

    public static void resendChunk(ServerPlayer player, RChunkRequestPacket request) {
        if (!consumePendingRequest(player, request)) {
            return;
        }
        if (!allowRequest(player.connection.connection)) {
            disableCache(player.connection.connection);
        }
        LevelChunk chunk = player.serverLevel().getChunkSource().getChunkNow(request.chunkX(), request.chunkZ());
        if (chunk == null) {
            return;
        }
        var packet = new ClientboundLevelChunkWithLightPacket(
                chunk,
                player.serverLevel().getChunkSource().getLightEngine(),
                null,
                null
        );
        player.trackChunk(new ChunkPos(request.chunkX(), request.chunkZ()), packet);
    }

    public static void remove(ServerPlayer player) {
        Connection connection = player.connection.connection;
        synchronized (LOCK) {
            STATES.remove(connection);
            CACHED_HASHES.remove(connection);
            MANIFESTS.remove(connection);
            PENDING_REQUESTS.remove(connection);
            REQUEST_WINDOWS.remove(connection);
        }
        RdiPendingChunkQueue.discard(connection);
    }

    private static void schedulePendingTimeout(ServerPlayer player) {
        var server = player.server;
        Connection connection = player.connection.connection;
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> server.execute(() -> {
            boolean timedOut;
            synchronized (LOCK) {
                timedOut = STATES.get(connection) == State.PENDING;
                if (timedOut) {
                    STATES.put(connection, State.DISABLED);
                    CACHED_HASHES.remove(connection);
                    MANIFESTS.remove(connection);
                    PENDING_REQUESTS.remove(connection);
                    REQUEST_WINDOWS.remove(connection);
                }
            }
            if (timedOut) {
                RdiPendingChunkQueue.drain(connection);
            }
        }));
    }

    private static LongSet readManifest(byte[] bytes) {
        if (bytes.length < Integer.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int count = buffer.getInt();
        if (count < 0 || buffer.remaining() != (long) count * Long.BYTES) {
            return null;
        }
        LongSet hashes = new LongOpenHashSet(count);
        for (int i = 0; i < count; i++) {
            hashes.add(buffer.getLong());
        }
        return hashes;
    }

    private static long registerPendingRequest(Connection connection, ServerPlayer player, int chunkX, int chunkZ, long hash) {
        Map<Long, PendingRequest> requests = PENDING_REQUESTS.computeIfAbsent(connection, ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        requests.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        if (requests.size() >= MAX_PENDING_REQUESTS) {
            return 0L;
        }
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        requests.put(requestId, new PendingRequest(
                chunkX,
                chunkZ,
                hash,
                now + REQUEST_TTL_MILLIS,
                player.level().dimension()
        ));
        return requestId;
    }

    private static boolean consumePendingRequest(ServerPlayer player, RChunkRequestPacket request) {
        Connection connection = player.connection.connection;
        synchronized (LOCK) {
            Map<Long, PendingRequest> requests = PENDING_REQUESTS.get(connection);
            if (requests == null) {
                return false;
            }
            PendingRequest pending = requests.remove(request.requestId());
            if (pending == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            return pending.expiresAtMillis() > now
                    && pending.chunkX() == request.chunkX()
                    && pending.chunkZ() == request.chunkZ()
                    && pending.contentHash() == request.contentHash()
                    && pending.dimension().equals(player.level().dimension());
        }
    }

    private static boolean allowRequest(Connection connection) {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            RequestWindow window = REQUEST_WINDOWS.computeIfAbsent(connection, ignored -> new RequestWindow());
            if (now - window.startedAtMillis >= 1000L) {
                window.startedAtMillis = now;
                window.count = 0;
            }
            window.count++;
            return window.count <= REQUESTS_PER_SECOND;
        }
    }

    private static void disableCache(Connection connection) {
        synchronized (LOCK) {
            if (STATES.get(connection) == State.DISABLED) {
                return;
            }
            STATES.put(connection, State.DISABLED);
            CACHED_HASHES.remove(connection);
            MANIFESTS.remove(connection);
            PENDING_REQUESTS.remove(connection);
            REQUEST_WINDOWS.remove(connection);
        }
    }
}
