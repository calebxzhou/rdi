package calebxzau.util.netty

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.CorruptedFrameException

fun ByteBuf.writeVarInt(value: Int): ByteBuf {
    var remaining = value
    while ((remaining and -128) != 0) {
        writeByte(remaining and 127 or 128)
        remaining = remaining ushr 7
    }
    writeByte(remaining)
    return this
}

fun ByteBuf.readVarInt(maxBytes: Int = 5): Int {
    return tryReadVarInt(maxBytes) ?: throw CorruptedFrameException("Incomplete VarInt")
}

fun ByteBuf.tryReadVarInt(maxBytes: Int = 5): Int? {
    val startIndex = readerIndex()
    var value = 0
    var position = 0
    var bytesRead = 0
    while (true) {
        if (!isReadable) {
            readerIndex(startIndex)
            return null
        }
        val currentByte = readUnsignedByte().toInt()
        value = value or ((currentByte and 0x7F) shl position)
        bytesRead++
        if ((currentByte and 0x80) == 0) return value
        if (bytesRead >= maxBytes) {
            readerIndex(startIndex)
            throw CorruptedFrameException("VarInt too big")
        }
        position += 7
    }
}

fun varIntSize(value: Int): Int = when {
    value and -128 == 0 -> 1
    value and -16384 == 0 -> 2
    value and -2097152 == 0 -> 3
    value and -268435456 == 0 -> 4
    else -> 5
}

fun ByteBuf.writeUtf8String(value: String): ByteBuf {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeVarInt(bytes.size)
    writeBytes(bytes)
    return this
}
