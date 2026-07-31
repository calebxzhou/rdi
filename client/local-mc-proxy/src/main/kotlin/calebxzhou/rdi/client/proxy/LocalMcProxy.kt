package calebxzhou.rdi.client.proxy

class LocalMcProxy(
    endpointProvider: () -> ProxyEndpoint,
    config: LocalMcProxyConfig = LocalMcProxyConfig(),
    onLog: (String) -> Unit = {}
) {
    private val server = LocalMcProxyServer(
        resolveEndpoint = endpointProvider,
        config = config,
        reportLog = { onLog("[LocalMcProxy] $it") }
    )

    fun start(): Result<String> = runCatching {
        server.start()
    }

    fun stop(): Result<Unit> = runCatching {
        server.stop()
    }
}
