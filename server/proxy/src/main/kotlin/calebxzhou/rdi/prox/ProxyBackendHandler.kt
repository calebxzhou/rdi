package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzhou.rdi.common.model.McVersion
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil

/**
 * Handler for backend (server-facing) connections
 */
class ProxyBackendHandler(
    private val frontendChannel: Channel,
    private val mcVersion: McVersion?
) : ChannelInboundHandlerAdapter() {
    private var state = MinecraftProtocolState.LOGIN
    private val packetRewriteEnabled = LIGHT_REWRITE_ENABLED && mcVersion == McVersion.V201
    private var compressionThreshold: Int? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        // Backend is ready, connection established
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val outbound = rewriteIfNeeded(ctx, msg as ByteBuf)

        // Forward data to frontend immediately with flush
        frontendChannel.writeAndFlush(outbound).addListener { future ->
            if (!future.isSuccess) {
                // Write failed, close connection
                lgr.info { "Failed to write to frontend: ${future.cause()?.message}" }
                ctx.channel().close()
            }
        }
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

    private fun rewriteIfNeeded(ctx: ChannelHandlerContext, frame: ByteBuf): ByteBuf {
        if (state == MinecraftProtocolState.LOGIN) {
            if (compressionThreshold == null) {
                val packetData = MinecraftCompression.decodePacketData(frame, null, ctx.alloc()).getOrElse {
                    lgr.warn { "Failed to decode Minecraft login packet, forwarding original: ${it.message}" }
                    return frame
                }
                try {
                    val parser = packetData.duplicate()
                    handleLoginPacket(parser.readVarInt(), parser)
                } finally {
                    packetData.release()
                }
            } else {
                val packetId = MinecraftCompression.peekPacketId(frame, compressionThreshold).getOrElse {
                    lgr.warn { "Failed to read compressed Minecraft login packet id, forwarding original: ${it.message}" }
                    return frame
                }
                handleLoginPacket(packetId, null)
            }
            return frame
        }
        if (!packetRewriteEnabled) return frame

        val packetId = MinecraftCompression.peekPacketId(frame, compressionThreshold).getOrElse {
            lgr.warn { "Failed to read Minecraft packet id, forwarding original: ${it.message}" }
            return frame
        }
        if (mcVersion != McVersion.V201 || packetId != LevelChunkWithLightRewriter1201.PACKET_ID) {
            return frame
        }

        val packetData = MinecraftCompression.decodePacketData(frame, compressionThreshold, ctx.alloc()).getOrElse {
            lgr.warn { "Failed to decode Minecraft packet frame, forwarding original: ${it.message}" }
            return frame
        }
        try {
            val rewrittenPacketData = LevelChunkWithLightRewriter1201.rewritePacketData(packetData, ctx.alloc()).getOrElse {
                lgr.warn { "Failed to rewrite 1.20.1 chunk light packet, forwarding original: ${it.message}" }
                return frame
            }
            try {
                val rewrittenFrame = MinecraftCompression.encodePacketData(rewrittenPacketData, compressionThreshold, ctx.alloc()).getOrElse {
                    lgr.warn { "Failed to encode rewritten Minecraft packet frame, forwarding original: ${it.message}" }
                    return frame
                }
                ReferenceCountUtil.release(frame)
                return rewrittenFrame
            } finally {
                rewrittenPacketData.release()
            }
        } finally {
            packetData.release()
        }
    }

    private fun handleLoginPacket(packetId: Int, payload: ByteBuf?) {
        when (packetId) {
            LOGIN_COMPRESSION_PACKET_ID -> {
                compressionThreshold = requireNotNull(payload).readVarInt().also {
                    require(it >= 0) { "Invalid Minecraft compression threshold: $it" }
                }
                lgr.info { "Backend enabled Minecraft compression threshold=$compressionThreshold" }
            }
            LOGIN_GAME_PROFILE_PACKET_ID -> {
                state = MinecraftProtocolState.PLAY
                lgr.info { "Backend connection entered PLAY state for ${mcVersion?.mcVer ?: "unsupported"}" }
            }
            LOGIN_ENCRYPTION_REQUEST_PACKET_ID ->
                error("Backend requested unsupported Minecraft encryption")
        }
    }

    private enum class MinecraftProtocolState {
        LOGIN,
        PLAY
    }

    private companion object {
        private const val LIGHT_REWRITE_ENABLED = false
        private const val LOGIN_ENCRYPTION_REQUEST_PACKET_ID = 0x01
        private const val LOGIN_GAME_PROFILE_PACKET_ID = 0x02
        private const val LOGIN_COMPRESSION_PACKET_ID = 0x03
    }
}
