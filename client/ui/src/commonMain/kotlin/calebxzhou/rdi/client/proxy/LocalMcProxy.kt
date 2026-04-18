package calebxzhou.rdi.client.proxy

expect object LocalMcProxy {
    val gameAddr: String

    fun start(onLog: (String) -> Unit)

    fun stop()
}
