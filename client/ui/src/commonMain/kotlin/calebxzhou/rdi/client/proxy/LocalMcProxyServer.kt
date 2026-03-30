package calebxzhou.rdi.client.proxy

import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.net.SERVER_NODES
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.net.BindException
import java.net.InetSocketAddress

internal data class ProxyEndpoint(
    val host: String,
    val port: Int
)

internal object LocalMcProxyCommon {
    const val LOCAL_BIND_HOST = "127.0.0.1"
    const val PREFERRED_BIND_PORT = 55667

    fun gameAddr(port: Int): String = "$LOCAL_BIND_HOST:$port"

    fun currentEndpointFromCarrier(): ProxyEndpoint {
        val gameAddr = (SERVER_NODES[CONF.carrier] ?: SERVER_NODES.getValue(0)).gameAddr
        return parseEndpoint(gameAddr)
    }

    private fun parseEndpoint(gameAddr: String): ProxyEndpoint {
        val normalized = gameAddr.removePrefix("tcp://").removePrefix("udp://")
        val delimiter = normalized.lastIndexOf(':')
        require(delimiter > 0 && delimiter < normalized.lastIndex) {
            "地图地址无效: $gameAddr"
        }
        val host = normalized.substring(0, delimiter)
        val port = normalized.substring(delimiter + 1).toIntOrNull()
            ?: throw IllegalArgumentException("地图端口无效: $gameAddr")
        return ProxyEndpoint(host, port)
    }
}

internal class LocalMcProxyServer(
    private val resolveEndpoint: () -> ProxyEndpoint = LocalMcProxy::currentEndpointFromCarrier,
    private val reportLog: (String) -> Unit = LocalMcProxy::reportLog,
    private val onListenPortChanged: (Int?) -> Unit = {}
) {
    private val lock = Any()

    @Volatile
    private var bossGroup: EventLoopGroup? = null

    @Volatile
    private var workerGroup: EventLoopGroup? = null

    @Volatile
    private var backendGroup: EventLoopGroup? = null

    @Volatile
    private var serverChannel: Channel? = null

    val gameAddr: String
        get() = LocalMcProxyCommon.gameAddr(currentListenPort() ?: LocalMcProxyCommon.PREFERRED_BIND_PORT)

    fun start() {
        synchronized(lock) {
            if (serverChannel?.isActive == true) return

            val nextBossGroup = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
            val nextWorkerGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
            val nextBackendGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())

            try {
                val bootstrap = ServerBootstrap()
                    .group(nextBossGroup, nextWorkerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .childHandler(LocalMcProxyServerInitializer(nextBackendGroup))

                val channel = try {
                    bootstrap.bind(
                        LocalMcProxyCommon.LOCAL_BIND_HOST,
                        LocalMcProxyCommon.PREFERRED_BIND_PORT
                    ).sync().channel()
                } catch (t: Throwable) {
                    if (!t.isBindException()) {
                        throw t
                    }
                    bootstrap.bind(LocalMcProxyCommon.LOCAL_BIND_HOST, 0).sync().channel().also {
                        val port = (it.localAddress() as InetSocketAddress).port
                        reportLog(
                            "preferred port ${LocalMcProxyCommon.PREFERRED_BIND_PORT} is occupied, fallback to $port"
                        )
                    }
                }

                bossGroup = nextBossGroup
                workerGroup = nextWorkerGroup
                backendGroup = nextBackendGroup
                serverChannel = channel
                onListenPortChanged((channel.localAddress() as InetSocketAddress).port)

                val endpoint = resolveEndpoint()
                reportLog("listening on $gameAddr -> ${endpoint.host}:${endpoint.port}")
            } catch (t: Throwable) {
                nextBossGroup.shutdownGracefully()
                nextWorkerGroup.shutdownGracefully()
                nextBackendGroup.shutdownGracefully()
                throw t
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            runCatching { serverChannel?.close()?.sync() }
            serverChannel = null
            bossGroup?.shutdownGracefully()
            workerGroup?.shutdownGracefully()
            backendGroup?.shutdownGracefully()
            bossGroup = null
            workerGroup = null
            backendGroup = null
            onListenPortChanged(null)
            reportLog("stopped")
        }
    }


    fun currentListenPort(): Int? =
        (serverChannel?.localAddress() as? InetSocketAddress)?.port

    private fun Throwable.isBindException(): Boolean =
        this is BindException || cause?.let { it is BindException } == true
}
