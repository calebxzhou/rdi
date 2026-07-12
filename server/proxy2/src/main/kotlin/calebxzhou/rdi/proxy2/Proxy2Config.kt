package calebxzhou.rdi.proxy2

data class Proxy2Config(
    val bindHost: String = proxy2Prop("rdi.proxy2.bindHost", env = "RDI_PROXY2_BIND_HOST") ?: "0.0.0.0",
    val httpPort: Int = proxy2IntProp("rdi.proxy2.httpPort", "RDI_PROXY2_HTTP_PORT", 65431),
    val stunPort: Int = proxy2IntProp("rdi.proxy2.stunPort", "RDI_PROXY2_STUN_PORT", 3478),
    val udpRelayPort: Int = proxy2IntProp("rdi.proxy2.udpRelayPort", "RDI_PROXY2_UDP_RELAY_PORT", 3479),
    val tcpRelayPort: Int = proxy2IntProp("rdi.proxy2.tcpRelayPort", "RDI_PROXY2_TCP_RELAY_PORT", 3480),
    val sessionTtlMs: Long = proxy2LongProp("rdi.proxy2.sessionTtlMs", "RDI_PROXY2_SESSION_TTL_MS", 60_000L),
    val cleanupIntervalMs: Long = proxy2LongProp("rdi.proxy2.cleanupIntervalMs", "RDI_PROXY2_CLEANUP_INTERVAL_MS", 10_000L),
) {
    companion object {
        fun load(): Proxy2Config = Proxy2Config()
    }
}

private fun proxy2Prop(name: String, env: String): String? =
    System.getProperty(name)?.takeIf(String::isNotBlank)
        ?: System.getenv(env)?.takeIf(String::isNotBlank)

private fun proxy2IntProp(name: String, env: String, default: Int): Int =
    proxy2Prop(name, env)?.toIntOrNull() ?: default

private fun proxy2LongProp(name: String, env: String, default: Long): Long =
    proxy2Prop(name, env)?.toLongOrNull() ?: default
