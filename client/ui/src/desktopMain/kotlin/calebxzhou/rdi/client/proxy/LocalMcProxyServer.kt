package calebxzhou.rdi.client.proxy

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.net.BindException
import java.net.InetSocketAddress

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
        get() = LocalMcProxy.gameAddr(currentListenPort() ?: LocalMcProxy.preferredBindPort)

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
                        LocalMcProxy.localBindHost,
                        LocalMcProxy.preferredBindPort
                    ).sync().channel()
                } catch (t: Throwable) {
                    if (!t.isBindException()) {
                        throw t
                    }
                    bootstrap.bind(LocalMcProxy.localBindHost, 0).sync().channel().also {
                        val port = (it.localAddress() as InetSocketAddress).port
                        reportLog(
                            "preferred port ${LocalMcProxy.preferredBindPort} is occupied, fallback to $port"
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
