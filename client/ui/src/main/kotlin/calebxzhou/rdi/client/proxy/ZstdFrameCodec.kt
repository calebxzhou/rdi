package calebxzhou.rdi.client.proxy

import com.github.luben.zstd.Zstd
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.MessageToByteEncoder

private const val ZSTD_MAGIC = 0x52445A53 // RDZS
private const val FLAG_COMPRESSED = 0x01

internal class ZstdFrameEncoder(
    private val level: Int = LocalMcProxyCompression.level,
    private val threshold: Int = LocalMcProxyCompression.threshold,
) : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val length = msg.readableBytes()
        if (length == 0) return

        val raw = ByteArray(length)
        msg.getBytes(msg.readerIndex(), raw)
        val compressed = length >= threshold
        val payload = if (compressed) Zstd.compress(raw, level) else raw

        out.writeInt(ZSTD_MAGIC)
        out.writeByte(if (compressed) FLAG_COMPRESSED else 0)
        writeVarInt(length, out)
        writeVarInt(payload.size, out)
        out.writeBytes(payload)
    }
}

internal class ZstdFrameDecoder(
    private val maxFrameSize: Int = LocalMcProxyCompression.maxFrameSize,
) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val frameStart = input.readerIndex()
        if (input.readableBytes() < 6) return

        val magic = input.readInt()
        if (magic != ZSTD_MAGIC) {
            throw CorruptedFrameException("invalid zstd frame magic: 0x${magic.toString(16)}")
        }

        val flags = input.readUnsignedByte().toInt()
        if ((flags and FLAG_COMPRESSED.inv()) != 0) {
            throw CorruptedFrameException("unknown zstd frame flags: $flags")
        }
        val uncompressedLength = readVarInt(input)
        if (uncompressedLength == null) {
            input.readerIndex(frameStart)
            return
        }
        val payloadLength = readVarInt(input)
        if (payloadLength == null) {
            input.readerIndex(frameStart)
            return
        }
        if (uncompressedLength < 0 || payloadLength < 0 || uncompressedLength > maxFrameSize || payloadLength > maxFrameSize) {
            throw CorruptedFrameException("invalid zstd frame size raw=$uncompressedLength payload=$payloadLength")
        }
        if (input.readableBytes() < payloadLength) {
            input.readerIndex(frameStart)
            return
        }

        val payload = ByteArray(payloadLength)
        input.readBytes(payload)
        if ((flags and FLAG_COMPRESSED) == 0) {
            if (payloadLength != uncompressedLength) {
                throw CorruptedFrameException("raw zstd frame size mismatch: $payloadLength != $uncompressedLength")
            }
            val buf = ctx.alloc().buffer(payload.size)
            buf.writeBytes(payload)
            out.add(buf)
            return
        }

        val decompressed = Zstd.decompress(payload, uncompressedLength)
        if (decompressed.size != uncompressedLength) {
            throw CorruptedFrameException("zstd decoded size mismatch: ${decompressed.size} != $uncompressedLength")
        }
        val buf = ctx.alloc().buffer(decompressed.size)
        buf.writeBytes(decompressed)
        out.add(buf)
    }
}

private fun writeVarInt(value: Int, out: ByteBuf) {
    var current = value
    while (true) {
        if ((current and 0x7F.inv()) == 0) {
            out.writeByte(current)
            return
        }
        out.writeByte((current and 0x7F) or 0x80)
        current = current ushr 7
    }
}

private fun readVarInt(input: ByteBuf): Int? {
    var value = 0
    var position = 0
    while (true) {
        if (!input.isReadable) return null
        val currentByte = input.readByte().toInt()
        value = value or ((currentByte and 0x7F) shl position)
        if ((currentByte and 0x80) == 0) return value
        position += 7
        if (position >= 32) {
            throw CorruptedFrameException("VarInt too big")
        }
    }
}
