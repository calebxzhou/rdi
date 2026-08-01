package calebxzhou.rdi.prox

import calebxzhou.rdi.mc.proxy.MinecraftFrameDecoder
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler

/**
 * Channel initializer for incoming client connections
 */
class ProxyServerInitializer(
    private val defaultBackendHost: String,
    private val defaultBackendPort: Int,
    private val backendGroup: EventLoopGroup
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        ch.config().setOption(ChannelOption.TCP_NODELAY, true)
        ch.config().setOption(ChannelOption.SO_KEEPALIVE, true)
        if(Const.DEBUG){
            //记录包的内容
            ch.pipeline().addLast(LoggingHandler(LogLevel.INFO))
        }
        ch.pipeline().addLast(
            // Use Minecraft framing to handle packet boundaries
            MinecraftFrameDecoder(),
            // Port extraction from Handshake packet
            DynamicProxyFrontendHandler(defaultBackendHost, defaultBackendPort, backendGroup)
        )
    }
}
