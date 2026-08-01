package calebxzhou.rdi.prox

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

/**
 * Handler for backend (server-facing) connections
 */
class ProxyBackendHandler(
    private val frontendChannel: Channel
) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        // Backend is ready, connection established
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ProxyFlowControl.pauseSourceIfTargetNotWritable(ctx.channel(), frontendChannel)
        frontendChannel.write(msg).addListener { future ->
            if (!future.isSuccess) {
                // Write failed, close connection
                lgr.info { "Failed to write to frontend: ${future.cause()?.message}" }
                ctx.channel().close()
            }
        }
        ProxyFlowControl.pauseSourceIfTargetNotWritable(ctx.channel(), frontendChannel)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        frontendChannel.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        ProxyFlowControl.resumePeerIfWritable(ctx.channel())
        ctx.fireChannelWritabilityChanged()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        closeOnFlush(frontendChannel)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        lgr.info { "Backend exception: ${cause.message}" }
        closeOnFlush(ctx.channel())
    }

    private fun closeOnFlush(ch: Channel) {
        if (ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE)
        }
    }
}
