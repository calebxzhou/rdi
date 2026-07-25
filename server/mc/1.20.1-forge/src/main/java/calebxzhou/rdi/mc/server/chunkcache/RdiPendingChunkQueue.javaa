package calebxzhou.rdi.mc.server.chunkcache;

import calebxzhou.rdi.mc.server.mixin.AChunkMap;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RdiPendingChunkQueue {
    private record Entry(ChunkMap chunkMap, ServerLevel level, ServerPlayer player, LevelChunk chunk) {
    }

    private static final Map<Connection, List<Entry>> QUEUES = new ConcurrentHashMap<>();

    private RdiPendingChunkQueue() {
    }

    public static void enqueue(Connection connection, ChunkMap chunkMap, ServerPlayer player, LevelChunk chunk) {
        QUEUES.compute(connection, (ignored, entries) -> {
            if (entries == null) {
                entries = new ArrayList<>();
            }
            entries.add(new Entry(chunkMap, ((AChunkMap) chunkMap).rdi$level(), player, chunk));
            return entries;
        });
    }

    public static void drain(Connection connection) {
        List<Entry> entries = QUEUES.remove(connection);
        if (entries == null) {
            return;
        }
        for (Entry entry : entries) {
            if (entry.player.connection.connection.isConnected() && entry.player.serverLevel() == entry.level) {
                ((AChunkMap) entry.chunkMap).rdi$playerLoadedChunk(entry.player, new MutableObject<>(), entry.chunk);
            }
        }
    }

    public static void discard(Connection connection) {
        QUEUES.remove(connection);
    }
}
