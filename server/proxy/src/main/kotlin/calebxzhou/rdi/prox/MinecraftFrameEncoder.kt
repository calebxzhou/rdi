package calebxzhou.rdi.prox

import calebxzau.util.netty.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

// Minecraft VarInt frame encoder - prepends packet length as VarInt
class MinecraftFrameEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val length = msg.readableBytes()
        out.writeVarInt(length)
        out.writeBytes(msg)
    }
}
