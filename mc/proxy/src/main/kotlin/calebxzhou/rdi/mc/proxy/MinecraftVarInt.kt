package calebxzhou.rdi.mc.proxy

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.CorruptedFrameException

internal fun readVarInt(input: ByteBuf, maxBytes: Int = 5): Int? {
    var value = 0
    var position = 0
    repeat(maxBytes) {
        if (!input.isReadable) return null
        val currentByte = input.readByte().toInt()
        value = value or ((currentByte and 0x7F) shl position)
        if ((currentByte and 0x80) == 0) return value
        position += 7
    }
    throw CorruptedFrameException("VarInt too big")
}

internal fun writeVarInt(value: Int, output: ByteBuf) {
    var current = value
    while (true) {
        if ((current and 0x7F.inv()) == 0) {
            output.writeByte(current)
            return
        }
        output.writeByte((current and 0x7F) or 0x80)
        current = current ushr 7
    }
}
