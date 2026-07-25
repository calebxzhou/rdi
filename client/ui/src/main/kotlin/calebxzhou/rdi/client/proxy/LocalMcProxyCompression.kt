package calebxzhou.rdi.client.proxy

internal object LocalMcProxyCompression {
    const val enabled: Boolean = true
    val level: Int = System.getProperty("rdi.zstd.level")?.toIntOrNull()?.coerceIn(1, 22) ?: 3
    val threshold: Int = System.getProperty("rdi.zstd.threshold")?.toIntOrNull()?.coerceAtLeast(0) ?: 256
    val maxFrameSize: Int = System.getProperty("rdi.zstd.maxFrameSize")?.toIntOrNull()?.coerceAtLeast(1024)
        ?: 8 * 1024 * 1024

    fun describe(): String =
        "zstd=$enabled level=$level threshold=$threshold maxFrameSize=$maxFrameSize"
}
