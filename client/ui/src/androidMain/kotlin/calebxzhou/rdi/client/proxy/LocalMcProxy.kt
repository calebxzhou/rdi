package calebxzhou.rdi.client.proxy

actual object LocalMcProxy {
    actual val gameAddr: String
        get() = ProxyEndpointResolver.currentEndpointFromCarrier().let { "${it.host}:${it.port}" }

    actual fun start(onLog: (String) -> Unit) = Unit

    actual fun stop() = Unit
}
