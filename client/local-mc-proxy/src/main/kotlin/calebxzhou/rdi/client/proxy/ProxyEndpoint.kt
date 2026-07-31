package calebxzhou.rdi.client.proxy

data class ProxyEndpoint(
    val host: String,
    val port: Int
) {
    companion object {
        fun parse(gameAddr: String): Result<ProxyEndpoint> = runCatching {
            val normalized = gameAddr.removePrefix("tcp://").removePrefix("udp://")
            val delimiter = normalized.lastIndexOf(':')
            require(delimiter > 0 && delimiter < normalized.lastIndex) {
                "房间地址无效: $gameAddr"
            }
            val host = normalized.substring(0, delimiter)
            val port = normalized.substring(delimiter + 1).toIntOrNull()
                ?: throw IllegalArgumentException("房间端口无效: $gameAddr")
            ProxyEndpoint(host, port)
        }
    }
}
