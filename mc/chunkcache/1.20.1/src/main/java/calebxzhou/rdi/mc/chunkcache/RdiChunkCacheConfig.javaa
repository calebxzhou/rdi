package calebxzhou.rdi.mc.chunkcache;

public final class RdiChunkCacheConfig {
    public static final boolean ENABLED = !Boolean.getBoolean("rdi.chunkCache.disable");
    public static final int MAX_SIZE_MB = Integer.getInteger("rdi.chunkCache.maxSizeMB", 2048);
    public static final int DCC_SIZE_LIMIT = Integer.getInteger("rdi.chunkCache.dccSizeLimit", 60);
    public static final int DCC_DISTANCE = Integer.getInteger("rdi.chunkCache.dccDistance", 5);
    public static final int DCC_TIMEOUT_SECONDS = Integer.getInteger("rdi.chunkCache.dccTimeoutSeconds", 60);

    private RdiChunkCacheConfig() {
    }
}
