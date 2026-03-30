package calebxzhou.rdi.client.proxy

actual object LocalMcProxy {
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


    actual internal fun currentEndpointFromCarrier(): ProxyEndpoint =
        LocalMcProxyCommon.currentEndpointFromCarrier()

    actual internal fun reportLog(message: String) {
        val formatted = "[LocalMcProxy] $message"
        println(formatted)
        runCatching { logSink(formatted) }
    }
}
