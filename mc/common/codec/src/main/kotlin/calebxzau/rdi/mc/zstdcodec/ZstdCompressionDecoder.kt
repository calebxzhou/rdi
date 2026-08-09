package calebxzau.rdi.mc.zstdcodec

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdDecompressCtx
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.DecoderException

internal class ZstdCompressionDecoder(
    private var threshold: Int,
    private var validateDecompressed: Boolean,
    private val varIntCodec: MinecraftVarIntCodec,
) : ByteToMessageDecoder() {
    private val decompressionContext = ZstdDecompressCtx()

    override fun decode(context: ChannelHandlerContext, input: ByteBuf, output: MutableList<Any>) {
        if (!input.isReadable) {
            return
        }

        val declaredSize = varIntCodec.read(input)
        if (declaredSize == 0) {
            val rawSize = input.readableBytes()
            if (rawSize > ZstdCompressionPipeline.MAXIMUM_UNCOMPRESSED_LENGTH) {
                throw DecoderException(
                    "Uncompressed packet size of $rawSize is larger than protocol maximum of ${ZstdCompressionPipeline.MAXIMUM_UNCOMPRESSED_LENGTH}"
                )
            }
            output.add(input.readRetainedSlice(input.readableBytes()))
            return
        }

        if (declaredSize < 0) {
            throw DecoderException("Negative uncompressed packet size: $declaredSize")
        }
        if (validateDecompressed && declaredSize < threshold) {
            throw DecoderException(
                "Badly compressed packet - size of $declaredSize is below server threshold of $threshold"
            )
        }
        if (declaredSize > ZstdCompressionPipeline.MAXIMUM_UNCOMPRESSED_LENGTH) {
            throw DecoderException(
                "Badly compressed packet - size of $declaredSize is larger than protocol maximum of ${ZstdCompressionPipeline.MAXIMUM_UNCOMPRESSED_LENGTH}"
            )
        }

        val compressedSize = input.readableBytes()
        if (compressedSize > ZstdCompressionPipeline.MAXIMUM_COMPRESSED_LENGTH) {
            throw DecoderException(
                "Badly compressed packet - compressed size of $compressedSize is larger than protocol maximum of ${ZstdCompressionPipeline.MAXIMUM_COMPRESSED_LENGTH}"
            )
        }

        val source = ByteArray(compressedSize)
        input.readBytes(source)
        val frameSize = Zstd.findFrameCompressedSize(source)
        if (frameSize != compressedSize.toLong()) {
            throw DecoderException(
                "Badly compressed packet - expected one Zstd frame of $compressedSize bytes, found $frameSize"
            )
        }

        val decoded = context.alloc().directBuffer(declaredSize, declaredSize)
        try {
            val destination = decoded.nioBuffer(0, declaredSize)
            val decodedSize = decompressionContext.decompressByteArrayToDirectByteBuffer(
                destination,
                0,
                declaredSize,
                source,
                0,
                source.size,
            )
            if (decodedSize != declaredSize) {
                throw DecoderException(
                    "Badly compressed packet - actual length of uncompressed payload $decodedSize does not match declared size $declaredSize"
                )
            }
            decoded.writerIndex(decodedSize)
            output.add(decoded)
        } catch (exception: Throwable) {
            decoded.release()
            throw exception
        }
    }

    fun updateThreshold(threshold: Int, validateDecompressed: Boolean) {
        this.threshold = threshold
        this.validateDecompressed = validateDecompressed
    }

    override fun handlerRemoved0(context: ChannelHandlerContext) {
        decompressionContext.close()
        super.handlerRemoved0(context)
    }
}
