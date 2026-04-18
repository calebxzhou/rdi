package calebxzhou.rdi.client.proxy

import calebxzhou.rdi.client.net.RServer

internal data class ProxyEndpoint(
    val host: String,
    val port: Int
)

internal object ProxyEndpointResolver {
    fun currentEndpointFromCarrier(): ProxyEndpoint {
        return parseEndpoint(RServer.currentGameAddr)
    }

    private fun parseEndpoint(gameAddr: String): ProxyEndpoint {
        val normalized = gameAddr.removePrefix("tcp://").removePrefix("udp://")
        val delimiter = normalized.lastIndexOf(':')
        require(delimiter > 0 && delimiter < normalized.lastIndex) {
            "房间地址无效: $gameAddr"
        }
        val host = normalized.substring(0, delimiter)
        val port = normalized.substring(delimiter + 1).toIntOrNull()
            ?: throw IllegalArgumentException("房间端口无效: $gameAddr")
        return ProxyEndpoint(host, port)
    }
}
