package calebxzhou.rdi.client.proxy

import calebxzhou.rdi.common.DEBUG
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.ReferenceCountUtil

internal class LocalMcProxyFrontendHandler(
    private val backendGroup: EventLoopGroup,
    private val resolveEndpoint: () -> ProxyEndpoint,
    private val reportLog: (String) -> Unit
) : ChannelInboundHandlerAdapter() {
    private var backendChannel: Channel? = null
    private val pendingBuffer = mutableListOf<Any>()
    private var firstMinecraftFrameHandled = false
    private val metrics = if (LocalMcProxyMetricsConfig.enabled) LocalMcProxyMetricsSession.create() else null

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (!firstMinecraftFrameHandled) {
            firstMinecraftFrameHandled = true
            val endpoint = resolveEndpoint()
            connectToBackend(ctx, endpoint)
            recordMetrics("c2s", msg)
            forwardToBackend(ctx, msg)
            if (metrics == null) {
                ctx.pipeline().remove(MinecraftFrameDecoder::class.java)
            }
            if(DEBUG)
            {
                reportLog(
                    "bridge ${ctx.channel().remoteAddress()} -> ${endpoint.host}:${endpoint.port}"
                )
            }else{
                reportLog("bridge ${ctx.channel().remoteAddress()}")
            }
            return
        }
        recordMetrics("c2s", msg)
        forwardToBackend(ctx, msg)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (backendChannel?.isActive == true) {
            closeOnFlush(backendChannel!!)
        }
        releasePendingBuffer()
        metrics?.closeAndSave()?.let { reportLog("net metrics saved: ${it.absolutePath}") }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        reportLog("frontend exception: ${cause.message ?: cause.javaClass.simpleName}")
        closeOnFlush(ctx.channel())
    }

    private fun connectToBackend(
        ctx: ChannelHandlerContext,
        endpoint: ProxyEndpoint
    ) {
        val frontendChannel = ctx.channel()
        if (!frontendChannel.isActive) {
            return
        }

        val bootstrap = Bootstrap()
            .group(backendGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.AUTO_READ, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .handler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    if (metrics != null) {
                        ch.pipeline().addLast(MinecraftFrameDecoder())
                    }
                    ch.pipeline().addLast(LocalMcProxyBackendHandler(frontendChannel, reportLog, metrics))
                }
            })

        val future = bootstrap.connect(endpoint.host, endpoint.port)
        backendChannel = future.channel()

        future.addListener { connectFuture ->
            if (!frontendChannel.isActive) {
                if (connectFuture.isSuccess) {
                    future.channel().close()
                }
                releasePendingBuffer()
                return@addListener
            }

            if (connectFuture.isSuccess) {
                flushPendingBuffer(ctx)
            } else {
                reportLog(
                    "backend connect failed ${endpoint.host}:${endpoint.port}: ${connectFuture.cause()?.message ?: "unknown"}"
                )
                releasePendingBuffer()
                frontendChannel.close()
            }
        }
    }

    private fun forwardToBackend(ctx: ChannelHandlerContext, msg: Any) {
        val frontendChannel = ctx.channel()
        if (!frontendChannel.isActive) {
            ReferenceCountUtil.release(msg)
            return
        }

        if (backendChannel?.isActive == true) {
            backendChannel?.writeAndFlush(msg)?.addListener { future ->
                if (!future.isSuccess) {
                    reportLog(
                        "backend write failed: ${future.cause()?.message ?: "unknown"}"
                    )
                    frontendChannel.close()
                }
            }
        } else {
            pendingBuffer += msg
        }
    }

    private fun flushPendingBuffer(ctx: ChannelHandlerContext) {
        if (pendingBuffer.isEmpty()) {
            return
        }
        val compositeBuf = ctx.alloc().compositeBuffer(pendingBuffer.size)
        pendingBuffer.forEach { bufferedMsg ->
            if (bufferedMsg is io.netty.buffer.ByteBuf) {
                compositeBuf.addComponent(true, bufferedMsg)
            } else {
                ReferenceCountUtil.release(bufferedMsg)
            }
        }
        pendingBuffer.clear()
        if (compositeBuf.isReadable) {
            backendChannel?.writeAndFlush(compositeBuf)?.addListener { future ->
                if (!future.isSuccess) {
                    reportLog(
                        "backend write failed: ${future.cause()?.message ?: "unknown"}"
                    )
                    ctx.channel().close()
                }
            }
        } else {
            compositeBuf.release()
        }
    }

    private fun releasePendingBuffer() {
        pendingBuffer.forEach(ReferenceCountUtil::release)
        pendingBuffer.clear()
    }

    private fun recordMetrics(direction: String, msg: Any) {
        if (metrics != null && msg is io.netty.buffer.ByteBuf) {
            metrics.record(direction, msg)
        }
    }

    private fun closeOnFlush(ch: Channel) {
        if (ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE)
        }
    }
}
