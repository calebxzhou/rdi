package calebxzau.rdi.mediaproc

import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer

data class DecodedRgbaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
)

object FfmpegAvifDecoder {
    private val readiness: Result<Unit> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            FFmpegFrameGrabber.tryLoad()
            val decoder: AVCodec? = avcodec_find_decoder(AV_CODEC_ID_AV1)
            check(decoder != null) { "AV1 decoder is unavailable" }
        }
    }

    fun isAvif(input: ByteArray): Boolean {
        if (input.size < 16 || !input.matchesAscii(4, "ftyp")) return false
        val boxSize = ByteBuffer.wrap(input, 0, Int.SIZE_BYTES).int
        if (boxSize < 16) return false
        val boxEnd = minOf(boxSize, input.size)
        if (input.isAvifBrand(8)) return true
        return (16 until boxEnd step 4).any{input.isAvifBrand(it)}
    }

    fun decode(input: ByteArray): Result<DecodedRgbaImage> = runCatching {
        require(isAvif(input)) { "Input is not an AVIF image" }
        readiness.getOrElse { throw MediaProcUnavailableException(it) }

        val grabber = FFmpegFrameGrabber(input.inputStream()).apply {
            pixelFormat = AV_PIX_FMT_RGBA
        }
        try {
            grabber.start()
            check(grabber.hasVideo()) { "AVIF has no image stream" }
            grabber.grabImage()?.toDecodedRgbaImage()
                ?: error("AVIF contains no decodable image frame")
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
        }
    }

    private fun Frame.toDecodedRgbaImage(): DecodedRgbaImage {
        check(imageWidth > 0 && imageHeight > 0) { "AVIF has invalid dimensions" }
        check(imageDepth == Frame.DEPTH_UBYTE && image.firstOrNull() is ByteBuffer) {
            "FFmpeg did not produce 8-bit RGBA pixels"
        }

        val rowBytes = Math.multiplyExact(imageWidth, RGBA_CHANNELS)
        check(imageStride >= rowBytes) { "FFmpeg produced an invalid RGBA row stride" }
        val pixels = ByteArray(Math.multiplyExact(rowBytes, imageHeight))
        val source = (image[0] as ByteBuffer).duplicate()
        repeat(imageHeight) { row ->
            source.position(row * imageStride)
            source.get(pixels, row * rowBytes, rowBytes)
        }
        return DecodedRgbaImage(imageWidth, imageHeight, pixels)
    }

    private fun ByteArray.isAvifBrand(offset: Int): Boolean =
        matchesAscii(offset, "avif") || matchesAscii(offset, "avis")

    private fun ByteArray.matchesAscii(offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= size &&
            value.indices.all { index -> this[offset + index].toInt() == value[index].code }

    private const val RGBA_CHANNELS = 4
}
