package calebxzhou.rdi.client.proxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

internal class LocalMcProxyBackendHandler(
    private val frontendChannel: Channel,
    private val reportLog: (String) -> Unit,
    private val metrics: LocalMcProxyMetricsSession? = null
) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (metrics != null && msg is ByteBuf) metrics.record("s2c", msg)
        LocalMcProxyFlowControl.pauseSourceIfTargetNotWritable(ctx.channel(), frontendChannel)
        frontendChannel.write(msg).addListener { future ->
            if (!future.isSuccess) {
                reportLog("frontend write failed: ${future.cause()?.message ?: "unknown"}")
                ctx.channel().close()
            }
        }
        LocalMcProxyFlowControl.pauseSourceIfTargetNotWritable(ctx.channel(), frontendChannel)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        frontendChannel.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        LocalMcProxyFlowControl.resumePeerIfWritable(ctx.channel())
        ctx.fireChannelWritabilityChanged()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        closeOnFlush(frontendChannel)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        reportLog("backend exception: ${cause.message ?: cause.javaClass.simpleName}")
        closeOnFlush(ctx.channel())
    }

    private fun closeOnFlush(channel: Channel) {
        if (channel.isActive) {
            channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }
}
