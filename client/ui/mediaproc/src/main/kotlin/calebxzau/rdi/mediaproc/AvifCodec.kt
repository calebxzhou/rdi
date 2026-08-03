package calebxzau.rdi.mediaproc

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.ByteBuffer

data class DecodedRgbaImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0 && height > 0) { "Invalid RGBA image dimensions: ${width}x$height" }
        val expectedSize = Math.multiplyExact(Math.multiplyExact(width, height), RGBA_CHANNELS)
        require(pixels.size == expectedSize) {
            "RGBA pixel buffer has ${pixels.size} bytes, expected $expectedSize"
        }
    }

    companion object {
        private const val RGBA_CHANNELS = 4
    }
}

object AvifCodec {
    private const val FTYP_OFFSET = 4
    private const val FTYP_HEADER_SIZE = 16
    private const val BRAND_SIZE = 4

   // private val encodeSemaphore = Semaphore(2)

    fun isAvif(input: ByteArray): Boolean = isAvif(ByteBuffer.wrap(input))

    fun isAvif(input: ByteBuffer): Boolean {
        val view = input.slice()
        if (view.remaining() < FTYP_HEADER_SIZE || !view.matchesAscii(FTYP_OFFSET, "ftyp")) return false

        val boxSize = view.readUInt32(0)
        if (boxSize < FTYP_HEADER_SIZE || boxSize > view.remaining() || boxSize == 1L) return false

        val boxEnd = boxSize.toInt()
        return view.isAvifBrand(8) ||
            (FTYP_HEADER_SIZE until boxEnd step BRAND_SIZE).any { view.isAvifBrand(it) }
    }

    fun encodePng(input: ByteArray): Result<ByteArray> =
      //  encodeSemaphore.withPermit {
            FfmpegAvifEncoder.encodePng(input)
       // }

    fun decode(input: ByteArray): Result<DecodedRgbaImage> =
        FfmpegAvifDecoder.decode(input)

    private fun ByteBuffer.isAvifBrand(offset: Int): Boolean =
        matchesAscii(offset, "avif") || matchesAscii(offset, "avis")

    private fun ByteBuffer.matchesAscii(offset: Int, value: String): Boolean =
        offset >= 0 && offset + value.length <= remaining() &&
            value.indices.all { index -> get(offset + index).toInt() == value[index].code }

    private fun ByteBuffer.readUInt32(offset: Int): Long =
        ((get(offset).toLong() and 0xFF) shl 24) or
            ((get(offset + 1).toLong() and 0xFF) shl 16) or
            ((get(offset + 2).toLong() and 0xFF) shl 8) or
            (get(offset + 3).toLong() and 0xFF)
}
