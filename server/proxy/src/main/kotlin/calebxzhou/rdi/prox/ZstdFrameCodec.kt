package calebxzhou.rdi.prox

import calebxzau.util.netty.tryReadVarInt
import calebxzau.util.netty.writeVarInt
import com.github.luben.zstd.Zstd
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException
import io.netty.handler.codec.MessageToByteEncoder
import io.netty.util.AttributeKey

private const val ZSTD_MAGIC = 0x52445A53 // RDZS
private const val FLAG_COMPRESSED = 0x01
private val ZSTD_FRAME_ENABLED = AttributeKey.valueOf<Boolean>("rdi.zstd.frame.enabled")

internal fun Channel.isZstdFrameEnabled(): Boolean = attr(ZSTD_FRAME_ENABLED).get() == true

internal fun Channel.disableZstdFrameEncoding() {
    attr(ZSTD_FRAME_ENABLED).set(false)
}

class ZstdFrameAutoDecoder(
    private val maxFrameSize: Int = Const.ZSTD_MAX_FRAME_SIZE,
) : ByteToMessageDecoder() {
    private var compressed: Boolean? = null

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        var mode = compressed
        if (mode == null) {
            if (input.readableBytes() < Int.SIZE_BYTES) return
            mode = input.getInt(input.readerIndex()) == ZSTD_MAGIC
            compressed = mode
            ctx.channel().attr(ZSTD_FRAME_ENABLED).set(mode)
            lgr.info { "client zstd frame mode=$mode" }
        }
        if (!mode) {
            if (input.isReadable) {
                out.add(input.readRetainedSlice(input.readableBytes()))
            }
            return
        }
        decodeZstdFrame(ctx, input, out, maxFrameSize)
    }
}

class ZstdFrameEncoder(
    private val level: Int = Const.ZSTD_LEVEL,
    private val threshold: Int = Const.ZSTD_THRESHOLD,
    private val onlyWhenDetected: Boolean = false,
) : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val length = msg.readableBytes()
        if (length == 0) return
        if (onlyWhenDetected && ctx.channel().attr(ZSTD_FRAME_ENABLED).get() != true) {
            out.writeBytes(msg, msg.readerIndex(), length)
            return
        }

        val raw = ByteArray(length)
        msg.getBytes(msg.readerIndex(), raw)
        val compressed = length >= threshold
        val payload = if (compressed) Zstd.compress(raw, level) else raw

        out.writeInt(ZSTD_MAGIC)
        out.writeByte(if (compressed) FLAG_COMPRESSED else 0)
        out.writeVarInt(length)
        out.writeVarInt(payload.size)
        out.writeBytes(payload)
    }
}

class ZstdFrameDecoder(
    private val maxFrameSize: Int = Const.ZSTD_MAX_FRAME_SIZE,
) : ByteToMessageDecoder() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        decodeZstdFrame(ctx, input, out, maxFrameSize)
    }
}

private fun decodeZstdFrame(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>, maxFrameSize: Int) {
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
    val uncompressedLength = input.tryReadVarInt()
    if (uncompressedLength == null) {
        input.readerIndex(frameStart)
        return
    }
    val payloadLength = input.tryReadVarInt()
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
