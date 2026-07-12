package calebxzhou.rdi.mc.server.chunkcache;

import calebxzhou.rdi.mc.chunkcache.RdiChunkCacheConfig;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public final class RdiDelayedChunkCache {
    private static final Map<ServerPlayer, RdiDelayedChunkCache> PLAYER_VIEWS = new WeakHashMap<>();
    private static final Set<ServerPlayer> IMMEDIATE_UNLOAD_PLAYERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final long NO_CACHE = -1L;
    private static TicketType<ChunkPos> ticketType;

    private final Long2LongLinkedOpenHashMap cache = new Long2LongLinkedOpenHashMap();

    private RdiDelayedChunkCache() {
        cache.defaultReturnValue(NO_CACHE);
    }

    public static TicketType<ChunkPos> ticketType() {
        int timeoutTicks = RdiChunkCacheConfig.DCC_TIMEOUT_SECONDS * 20;
        TicketType<ChunkPos> current = ticketType;
        if (current == null || current.timeout() != timeoutTicks) {
            current = TicketType.create("rdi_dcc", Comparator.comparingLong(ChunkPos::toLong), timeoutTicks);
            ticketType = current;
        }
        return current;
    }

    public static boolean delayUnload(ServerPlayer player, ChunkPos pos, int viewDistance, TicketPlacer ticketPlacer) {
        if (!RdiChunkCacheConfig.ENABLED
                || IMMEDIATE_UNLOAD_PLAYERS.contains(player)
                || RdiChunkCacheConfig.DCC_DISTANCE <= 0
                || RdiChunkCacheConfig.DCC_SIZE_LIMIT <= 0) {
            return false;
        }
        ChunkPos center = player.chunkPosition();
        if (!isInRange(pos, center, viewDistance + RdiChunkCacheConfig.DCC_DISTANCE)) {
            return false;
        }
        ticketPlacer.putTicket(pos, RdiChunkCacheConfig.DCC_TIMEOUT_SECONDS * 20);
        PLAYER_VIEWS.computeIfAbsent(player, ignored -> new RdiDelayedChunkCache())
                .cache.put(pos.toLong(), System.currentTimeMillis());
        return true;
    }

    public static boolean consumeCached(ServerPlayer player, ChunkPos pos) {
        if (IMMEDIATE_UNLOAD_PLAYERS.contains(player)) {
            return false;
        }
        RdiDelayedChunkCache view = PLAYER_VIEWS.get(player);
        return view != null && view.cache.remove(pos.toLong()) != NO_CACHE;
    }

    public static boolean isCached(ServerPlayer player, ChunkPos pos) {
        RdiDelayedChunkCache view = PLAYER_VIEWS.get(player);
        return view != null && view.cache.containsKey(pos.toLong());
    }

    public static void tick(ServerPlayer player, int viewDistance, EvictionConsumer evictionConsumer) {
        if (!RdiChunkCacheConfig.ENABLED) {
            return;
        }
        RdiDelayedChunkCache view = PLAYER_VIEWS.get(player);
        if (view != null) {
            view.evict(player.chunkPosition(), viewDistance, evictionConsumer);
        }
    }

    public static void remove(ServerPlayer player) {
        PLAYER_VIEWS.remove(player);
        IMMEDIATE_UNLOAD_PLAYERS.remove(player);
    }

    public static void beginImmediateUnload(ServerPlayer player) {
        PLAYER_VIEWS.remove(player);
        IMMEDIATE_UNLOAD_PLAYERS.add(player);
    }

    public static void endImmediateUnload(ServerPlayer player) {
        IMMEDIATE_UNLOAD_PLAYERS.remove(player);
    }

    @FunctionalInterface
    public interface TicketPlacer {
        void putTicket(ChunkPos pos, int ticks);
    }

    @FunctionalInterface
    public interface EvictionConsumer {
        void evict(ChunkPos pos);
    }

    private void evict(ChunkPos center, int viewDistance, EvictionConsumer evictionConsumer) {
        long now = System.currentTimeMillis();
        long timeoutMillis = TimeUnit.SECONDS.toMillis(RdiChunkCacheConfig.DCC_TIMEOUT_SECONDS);
        ObjectIterator<Long2LongMap.Entry> it = Long2LongMaps.fastIterator(cache);
        while (it.hasNext()) {
            Long2LongMap.Entry entry = it.next();
            ChunkPos pos = new ChunkPos(entry.getLongKey());
            if (isInRange(pos, center, viewDistance)) {
                continue;
            }
            boolean evict = entry.getLongValue() <= now - timeoutMillis
                    || !isInRange(pos, center, viewDistance + RdiChunkCacheConfig.DCC_DISTANCE)
                    || cache.size() > RdiChunkCacheConfig.DCC_SIZE_LIMIT;
            if (evict) {
                it.remove();
                evictionConsumer.evict(pos);
            }
        }
    }

    private static boolean isInRange(ChunkPos pos, ChunkPos center, int viewDistance) {
        return ChunkMap.isChunkInRange(pos.x, pos.z, center.x, center.z, viewDistance);
    }
}
