package calebxzhou.rdi.mc.proxy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException

class MinecraftFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val frameStartIndex = buffer.readerIndex()
        val length = readVarInt(buffer, maxBytes = 3)
        if (length == null) {
            buffer.readerIndex(frameStartIndex)
            return
        }
        if (length < 0) throw CorruptedFrameException("negative minecraft frame length: $length")

        val frameHeaderEndIndex = buffer.readerIndex()
        if (buffer.readableBytes() < length) {
            buffer.readerIndex(frameStartIndex)
            return
        }

        val totalFrameLength = frameHeaderEndIndex - frameStartIndex + length
        buffer.readerIndex(frameStartIndex)
        out += buffer.readRetainedSlice(totalFrameLength)
    }
}
