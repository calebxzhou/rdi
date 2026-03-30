package calebxzhou.rdi.client.proxy

expect object LocalMcProxy {
    val gameAddr: String

    fun start(onLog: (String) -> Unit)

    fun stop()

    internal fun currentEndpointFromCarrier(): ProxyEndpoint

    internal fun reportLog(message: String)
}
