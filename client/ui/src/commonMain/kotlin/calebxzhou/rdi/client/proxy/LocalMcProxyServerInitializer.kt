package calebxzhou.rdi.client.proxy

import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel

class LocalMcProxyServerInitializer(
    private val backendGroup: EventLoopGroup
) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        ch.config().setOption(ChannelOption.TCP_NODELAY, true)
        ch.config().setOption(ChannelOption.SO_KEEPALIVE, true)
        ch.pipeline().addLast(
            MinecraftFrameDecoder(),
            LocalMcProxyFrontendHandler(backendGroup)
        )
    }
}
