package calebxzhou.rdi.client.proxy

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class MinecraftFrameEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val length = msg.readableBytes()
        writeVarInt(length, out)
        out.writeBytes(msg)
    }

    private fun writeVarInt(value: Int, buffer: ByteBuf) {
        var current = value
        while (true) {
            if ((current and 0x7F.inv()) == 0) {
                buffer.writeByte(current)
                return
            }
            buffer.writeByte((current and 0x7F) or 0x80)
            current = current ushr 7
        }
    }
}
