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
    private val resolveEndpoint: () -> ProxyEndpoint,
    private val config: LocalMcProxyConfig,
    private val reportLog: (String) -> Unit
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

    fun start(): String = synchronized(lock) {
        if (serverChannel?.isActive == true) return@synchronized gameAddr()
        resolveEndpoint()

        val nextBossGroup = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        val nextWorkerGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
        val nextBackendGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())

        try {
            val bootstrap = ServerBootstrap()
                .group(nextBossGroup, nextWorkerGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_BACKLOG, 100)
                .childHandler(
                    LocalMcProxyServerInitializer(
                        backendGroup = nextBackendGroup,
                        resolveEndpoint = resolveEndpoint,
                        config = config,
                        reportLog = reportLog
                    )
                )

            val channel = try {
                bootstrap.bind(config.localBindHost, config.preferredBindPort).sync().channel()
            } catch (t: Throwable) {
                if (!t.isBindException()) throw t
                bootstrap.bind(config.localBindHost, 0).sync().channel().also {
                    val port = (it.localAddress() as InetSocketAddress).port
                    reportLog("preferred port ${config.preferredBindPort} is occupied, fallback to $port")
                }
            }

            bossGroup = nextBossGroup
            workerGroup = nextWorkerGroup
            backendGroup = nextBackendGroup
            serverChannel = channel

            reportLog("listening on ${gameAddr()}, ${config.describeCompression()}")
            gameAddr()
        } catch (t: Throwable) {
            nextBossGroup.shutdownGracefully()
            nextWorkerGroup.shutdownGracefully()
            nextBackendGroup.shutdownGracefully()
            throw t
        }
    }

    fun stop() = synchronized(lock) {
        val closeResult = runCatching { serverChannel?.close()?.sync() }
        serverChannel = null
        bossGroup?.shutdownGracefully()
        workerGroup?.shutdownGracefully()
        backendGroup?.shutdownGracefully()
        bossGroup = null
        workerGroup = null
        backendGroup = null
        reportLog("stopped")
        closeResult.getOrThrow()
    }

    private fun gameAddr(): String {
        val port = (serverChannel?.localAddress() as? InetSocketAddress)?.port ?: config.preferredBindPort
        return "${config.localBindHost}:$port"
    }

    private fun Throwable.isBindException(): Boolean =
        this is BindException || cause is BindException
}
