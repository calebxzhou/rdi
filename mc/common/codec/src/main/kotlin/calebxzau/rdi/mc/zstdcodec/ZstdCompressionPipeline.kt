package calebxzau.rdi.mc.zstdcodec

import com.github.luben.zstd.Zstd
import io.netty.channel.Channel
import io.netty.channel.ChannelPipeline
import io.netty.channel.local.LocalChannel
import io.netty.channel.local.LocalServerChannel

/**
 * Installs the RDI Zstd replacement for Minecraft's compression handlers.
 *
 * The outer Minecraft frame remains owned by the vanilla splitter/prepender;
 * these handlers only read and write Minecraft's inner compression envelope.
 */
object ZstdCompressionPipeline {
    const val MAXIMUM_COMPRESSED_LENGTH: Int = 2 * 1024 * 1024
    const val MAXIMUM_UNCOMPRESSED_LENGTH: Int = 8 * 1024 * 1024

    private const val DECOMPRESS_HANDLER_NAME = "decompress"
    private const val COMPRESS_HANDLER_NAME = "compress"
    private const val SPLITTER_HANDLER_NAME = "splitter"
    private const val PREPENDER_HANDLER_NAME = "prepender"
    private const val ZSTD_MAGIC_NUMBER = -47205080

    /**
     * Verifies native loading during each mod's initialization.
     *
     * zstd-jni 1.5.7-11 does not expose a `versionNumber()` method. Calling
     * the native magic number keeps this self-test independent of that missing
     * convenience API. A broken native setup is fatal because the matching
     * RDI protocol has no Deflate fallback.
     */
    @JvmStatic
    fun verifyNativeLoaded(): Int {
        val magicNumber = Zstd.magicNumber()
        check(magicNumber == ZSTD_MAGIC_NUMBER) {
            "zstd-jni returned an invalid native magic number: $magicNumber"
        }
        return magicNumber
    }

    @JvmStatic
    fun setup(
        channel: Channel,
        threshold: Int,
        validateDecompressed: Boolean,
        varIntCodec: MinecraftVarIntCodec,
    ) {
        val pipeline = channel.pipeline()
        if (channel is LocalChannel || channel is LocalServerChannel) {
            removeOwnedHandlers(pipeline)
            return
        }

        if (threshold < 0) {
            removeOwnedHandlers(pipeline)
            return
        }

        installDecoder(pipeline, threshold, validateDecompressed, varIntCodec)
        installEncoder(pipeline, threshold, varIntCodec)
    }

    private fun installDecoder(
        pipeline: ChannelPipeline,
        threshold: Int,
        validateDecompressed: Boolean,
        varIntCodec: MinecraftVarIntCodec,
    ) {
        val handler = pipeline.get(DECOMPRESS_HANDLER_NAME)
        when (handler) {
            is ZstdCompressionDecoder -> handler.updateThreshold(threshold, validateDecompressed)
            null -> pipeline.addAfter(
                SPLITTER_HANDLER_NAME,
                DECOMPRESS_HANDLER_NAME,
                ZstdCompressionDecoder(threshold, validateDecompressed, varIntCodec),
            )
            else -> pipeline.replace(
                DECOMPRESS_HANDLER_NAME,
                DECOMPRESS_HANDLER_NAME,
                ZstdCompressionDecoder(threshold, validateDecompressed, varIntCodec),
            )
        }
    }

    private fun installEncoder(
        pipeline: ChannelPipeline,
        threshold: Int,
        varIntCodec: MinecraftVarIntCodec,
    ) {
        val handler = pipeline.get(COMPRESS_HANDLER_NAME)
        when (handler) {
            is ZstdCompressionEncoder -> handler.updateThreshold(threshold)
            null -> pipeline.addAfter(
                PREPENDER_HANDLER_NAME,
                COMPRESS_HANDLER_NAME,
                ZstdCompressionEncoder(threshold, varIntCodec),
            )
            else -> pipeline.replace(
                COMPRESS_HANDLER_NAME,
                COMPRESS_HANDLER_NAME,
                ZstdCompressionEncoder(threshold, varIntCodec),
            )
        }
    }

    private fun removeOwnedHandlers(pipeline: ChannelPipeline) {
        if (pipeline.get(DECOMPRESS_HANDLER_NAME) is ZstdCompressionDecoder) {
            pipeline.remove(DECOMPRESS_HANDLER_NAME)
        }
        if (pipeline.get(COMPRESS_HANDLER_NAME) is ZstdCompressionEncoder) {
            pipeline.remove(COMPRESS_HANDLER_NAME)
        }
    }
}
