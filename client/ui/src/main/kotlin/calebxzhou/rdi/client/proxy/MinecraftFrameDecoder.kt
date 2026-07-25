package calebxzhou.rdi.client.proxy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import kotlin.collections.plusAssign

class MinecraftFrameDecoder : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val frameStartIndex = buffer.readerIndex()
        buffer.markReaderIndex()

        val length = readVarInt(buffer)
        if (length == -1) {
            buffer.readerIndex(frameStartIndex)
            return
        }

        val frameHeaderEndIndex = buffer.readerIndex()
        if (buffer.readableBytes() < length) {
            buffer.readerIndex(frameStartIndex)
            return
        }

        val totalFrameLength = frameHeaderEndIndex - frameStartIndex + length
        buffer.readerIndex(frameStartIndex)
        out += buffer.readRetainedSlice(totalFrameLength)
    }

    private fun readVarInt(buffer: ByteBuf): Int {
        var value = 0
        var position = 0

        while (true) {
            if (!buffer.isReadable) {
                return -1
            }

            val currentByte = buffer.readByte().toInt()
            value = value or ((currentByte and 0x7F) shl position)

            if ((currentByte and 0x80) == 0) {
                break
            }

            position += 7
            if (position >= 21) {
                throw RuntimeException("VarInt too big")
            }
        }

        return value
    }
}
