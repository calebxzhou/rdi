package calebxzhou.rdi.prox

object Const {
    const val MODID = "rdi"

    //是否为调试模式,本地用
    @JvmStatic
    val DEBUG = System.getProperty("rdi.debug").toBoolean()

    @JvmField
    val SERVER_PORT = System.getProperty("rdi.port")?.toIntOrNull() ?: 65230

    @JvmField
    val ZSTD_LEVEL = System.getProperty("rdi.zstd.level")?.toIntOrNull()?.coerceIn(1, 22) ?: 3

    @JvmField
    val ZSTD_THRESHOLD = System.getProperty("rdi.zstd.threshold")?.toIntOrNull()?.coerceAtLeast(0) ?: 256

    @JvmField
    val ZSTD_MAX_FRAME_SIZE = System.getProperty("rdi.zstd.maxFrameSize")?.toIntOrNull()?.coerceAtLeast(1024)
        ?: 8 * 1024 * 1024
}
