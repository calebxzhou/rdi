package calebxzhou.rdi.client.proxy

import java.io.File

data class LocalMcProxyConfig(
    val localBindHost: String = "127.0.0.1",
    val preferredBindPort: Int = 55667,
    val metricsEnabled: Boolean = System.getProperty("rdi.netMetrics").toBoolean(),
    val metricsDir: File? = null
)
