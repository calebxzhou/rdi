package calebxzhou.rdi.client.proxy

import calebxzhou.rdi.common.DEBUG

actual object LocalMcProxy {
    private const val LOCAL_BIND_HOST = "127.0.0.1"
    private const val PREFERRED_BIND_PORT = 55667

    @Volatile
    private var logSink: (String) -> Unit = {}

    private val server = LocalMcProxyServer()

    actual val gameAddr: String
        get() = server.gameAddr

    actual fun start(onLog: (String) -> Unit) {
        logSink = onLog
        server.start()
    }

    actual fun stop() {
        server.stop()
    }

    internal fun currentEndpointFromCarrier(): ProxyEndpoint =
        ProxyEndpointResolver.currentEndpointFromCarrier()

    internal fun reportLog(message: String) {
        if(!DEBUG) return
        val formatted = "[LocalMcProxy] $message"
        println(formatted)
        runCatching { logSink(formatted) }
    }

    internal fun gameAddr(port: Int): String = "$LOCAL_BIND_HOST:$port"

    internal val localBindHost: String
        get() = LOCAL_BIND_HOST

    internal val preferredBindPort: Int
        get() = PREFERRED_BIND_PORT
}
