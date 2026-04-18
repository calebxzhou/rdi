package calebxzhou.rdi.client.proxy

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

class LocalMcProxyBackendHandler(
    private val frontendChannel: Channel
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        frontendChannel.writeAndFlush(msg).addListener { future ->
            if (!future.isSuccess) {
                LocalMcProxy.reportLog(
                    "frontend write failed: ${future.cause()?.message ?: "unknown"}"
                )
                ctx.channel().close()
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        closeOnFlush(frontendChannel)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        LocalMcProxy.reportLog("backend exception: ${cause.message ?: cause.javaClass.simpleName}")
        closeOnFlush(ctx.channel())
    }

    private fun closeOnFlush(ch: Channel) {
        if (ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE)
        }
    }
}
