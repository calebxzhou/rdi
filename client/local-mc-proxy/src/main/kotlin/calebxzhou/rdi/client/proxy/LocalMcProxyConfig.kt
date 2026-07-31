package calebxzhou.rdi.client.proxy

import java.io.File

data class LocalMcProxyConfig(
    val localBindHost: String = "127.0.0.1",
    val preferredBindPort: Int = 55667,
    val compressionEnabled: Boolean = true,
    val compressionLevel: Int = System.getProperty("rdi.zstd.level")?.toIntOrNull()?.coerceIn(1, 22) ?: 3,
    val compressionThreshold: Int = System.getProperty("rdi.zstd.threshold")?.toIntOrNull()?.coerceAtLeast(0) ?: 256,
    val maxFrameSize: Int = System.getProperty("rdi.zstd.maxFrameSize")?.toIntOrNull()?.coerceAtLeast(1024)
        ?: 8 * 1024 * 1024,
    val metricsEnabled: Boolean = System.getProperty("rdi.netMetrics").toBoolean(),
    val metricsDir: File? = null
) {
    internal fun describeCompression(): String =
        "zstd=$compressionEnabled level=$compressionLevel threshold=$compressionThreshold maxFrameSize=$maxFrameSize"
}
