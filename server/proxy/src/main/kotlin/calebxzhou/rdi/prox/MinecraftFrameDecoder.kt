package calebxzhou.rdi.prox

import calebxzau.util.netty.tryReadVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

// Minecraft VarInt frame decoder - decodes packet length from VarInt prefix
class MinecraftFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val frameStartIndex = buffer.readerIndex()
        // Read VarInt length
        val length = buffer.tryReadVarInt(maxBytes = 3)
        if (length == null) {
            buffer.readerIndex(frameStartIndex)
            return // Not enough bytes to read VarInt
        }

        val frameHeaderEndIndex = buffer.readerIndex()

        // Check if full packet is available
        if (buffer.readableBytes() < length) {
            buffer.readerIndex(frameStartIndex)
            return // Wait for more data
        }

        val totalFrameLength = frameHeaderEndIndex - frameStartIndex + length
        buffer.readerIndex(frameStartIndex)
        out.add(buffer.readRetainedSlice(totalFrameLength))
    }

}
