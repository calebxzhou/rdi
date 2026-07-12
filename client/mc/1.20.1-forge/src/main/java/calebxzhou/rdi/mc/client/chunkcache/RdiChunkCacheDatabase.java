package calebxzhou.rdi.mc.client.chunkcache;

import com.github.luben.zstd.Zstd;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class RdiChunkCacheDatabase implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getLogger("rdi-chunk-cache-db");
    private static final int FORMAT_VERSION = 1;
    private static final byte[] VERSION_KEY = "RDI_CHUNK_CACHE_FORMAT_VERSION".getBytes(StandardCharsets.UTF_8);
    private static final int TIMESTAMP_BYTES = 8;

    private final DB db;
    private final Path dbDir;
    private final long maxSizeBytes;

    public RdiChunkCacheDatabase(Path dir, long maxSizeBytes) throws IOException {
        Options options = new Options()
                .createIfMissing(true)
                .cacheSize(32L * 1024L * 1024L);
        db = Iq80DBFactory.factory.open(dir.toFile(), options);
        dbDir = dir;
        this.maxSizeBytes = maxSizeBytes;
        migrateIfNeeded();
    }

    private void migrateIfNeeded() {
        byte[] stored = db.get(VERSION_KEY);
        int storedVersion = stored != null && stored.length == 4 ? ByteBuffer.wrap(stored).getInt() : -1;
        if (storedVersion == FORMAT_VERSION) {
            return;
        }

        try (DBIterator it = db.iterator()) {
            List<byte[]> keys = new ArrayList<>();
            for (it.seekToFirst(); it.hasNext(); it.next()) {
                keys.add(it.peekNext().getKey());
            }
            for (byte[] key : keys) {
                db.delete(key);
            }
        } catch (IOException e) {
            LOGGER.error("clear old chunk cache failed", e);
        }
        db.put(VERSION_KEY, ByteBuffer.allocate(4).putInt(FORMAT_VERSION).array());
    }

    public void put(long hash, byte[] data) {
        byte[] compressed = Zstd.compress(data, 6);
        byte[] value = new byte[TIMESTAMP_BYTES + compressed.length];
        ByteBuffer.wrap(value).putLong(System.currentTimeMillis());
        System.arraycopy(compressed, 0, value, TIMESTAMP_BYTES, compressed.length);
        db.put(longToBytes(hash), value);
    }

    @Nullable
    public byte[] get(long hash) {
        byte[] value = db.get(longToBytes(hash));
        if (value == null) {
            return null;
        }
        if (value.length <= TIMESTAMP_BYTES) {
            db.delete(longToBytes(hash));
            return null;
        }

        byte[] compressed = new byte[value.length - TIMESTAMP_BYTES];
        System.arraycopy(value, TIMESTAMP_BYTES, compressed, 0, compressed.length);
        long size = Zstd.decompressedSize(compressed);
        if (size <= 0 || size > 64L * 1024L * 1024L) {
            db.delete(longToBytes(hash));
            return null;
        }
        return Zstd.decompress(compressed, (int) size);
    }

    public void delete(long hash) {
        db.delete(longToBytes(hash));
    }

    public LongSet getAllHashes() {
        LongSet hashes = new LongOpenHashSet();
        try (DBIterator it = db.iterator()) {
            for (it.seekToFirst(); it.hasNext(); it.next()) {
                byte[] key = it.peekNext().getKey();
                if (key.length == Long.BYTES) {
                    hashes.add(bytesToLong(key));
                }
            }
        } catch (IOException e) {
            LOGGER.error("read chunk cache hashes failed", e);
        }
        return hashes;
    }

    public boolean evictIfNeeded() {
        long size = approximateSize();
        if (maxSizeBytes <= 0 || size <= maxSizeBytes) {
            return false;
        }

        long targetSize = (long) (maxSizeBytes * 0.7);
        long bytesToFree = size - targetSize;
        record Entry(byte[] key, long timestamp, int size) {
        }
        List<Entry> entries = new ArrayList<>();
        try (DBIterator it = db.iterator()) {
            for (it.seekToFirst(); it.hasNext(); it.next()) {
                byte[] key = it.peekNext().getKey();
                if (key.length != Long.BYTES) {
                    continue;
                }
                byte[] value = it.peekNext().getValue();
                long timestamp = value != null && value.length >= TIMESTAMP_BYTES
                        ? ByteBuffer.wrap(value, 0, TIMESTAMP_BYTES).getLong()
                        : 0L;
                entries.add(new Entry(key, timestamp, (value == null ? 0 : value.length) + key.length));
            }
        } catch (IOException e) {
            LOGGER.error("scan chunk cache failed", e);
            return false;
        }

        entries.sort(Comparator.comparingLong(Entry::timestamp));
        long freed = 0;
        for (Entry entry : entries) {
            if (freed >= bytesToFree) {
                break;
            }
            db.delete(entry.key);
            freed += entry.size;
        }
        try {
            db.compactRange(null, null);
        } catch (Exception ignored) {
        }
        return freed > 0;
    }

    private long approximateSize() {
        try (Stream<Path> walk = Files.walk(dbDir)) {
            return walk.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (IOException e) {
            LOGGER.error("close chunk cache failed", e);
        }
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private static long bytesToLong(byte[] value) {
        return ByteBuffer.wrap(value).getLong();
    }
}
