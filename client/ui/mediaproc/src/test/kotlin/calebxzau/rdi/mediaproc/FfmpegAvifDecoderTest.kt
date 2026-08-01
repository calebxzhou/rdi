package calebxzau.rdi.mediaproc

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegAvifDecoderTest {
    @Test
    fun detectsAvifBrand() {
        assertTrue(FfmpegAvifDecoder.isAvif(ftyp("avif")))
        assertTrue(FfmpegAvifDecoder.isAvif(ftyp("mif1", "avis")))
        assertFalse(FfmpegAvifDecoder.isAvif(ftyp("mif1", "heic")))
        assertFalse(FfmpegAvifDecoder.isAvif(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun decodesBundledBackgroundToRgba() {
        assumeWindowsX64()
        val input = javaClass.getResourceAsStream("/assets/bg.avif")!!.use { it.readBytes() }

        val decoded = FfmpegAvifDecoder.decode(input).getOrThrow()

        assertEquals(1280, decoded.width)
        assertEquals(720, decoded.height)
        assertEquals(decoded.width * decoded.height * 4, decoded.pixels.size)
        assertTrue((3 until decoded.pixels.size step 4).all { decoded.pixels[it] == 0xFF.toByte() })
    }

    @Test
    fun rejectsInvalidAvif() {
        assumeWindowsX64()
        assertTrue(FfmpegAvifDecoder.decode(ftyp("avif")).isFailure)
    }

    private fun ftyp(majorBrand: String, vararg compatibleBrands: String): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        return ByteArray(size).apply {
            this[3] = size.toByte()
            "ftyp".writeTo(this, 4)
            majorBrand.writeTo(this, 8)
            compatibleBrands.forEachIndexed { index, brand ->
                brand.writeTo(this, 16 + index * 4)
            }
        }
    }

    private fun String.writeTo(target: ByteArray, offset: Int) {
        forEachIndexed { index, char -> target[offset + index] = char.code.toByte() }
    }

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
