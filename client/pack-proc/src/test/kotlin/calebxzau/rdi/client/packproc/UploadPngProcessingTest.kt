package calebxzau.rdi.client.packproc

import calebxzau.rdi.mediaproc.AvifCodec
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class UploadPngProcessingTest {
    @Test
    fun keepsSmallPngBytesUntouched() {
        val input = ByteArray(50 * 1024) { it.toByte() }

        assertContentEquals(input, processUploadPng(input, "small.png"))
    }

    @Test
    fun keepsOriginalPngWhenAvifEncodingFails() {
        val input = ByteArray(50 * 1024 + 1) { (it * 31).toByte() }

        assertContentEquals(input, processUploadPng(input, "broken.png"))
    }

    @Test
    fun encodesLargePngAsAvifWithoutChangingEntryName() {
        assumeWindowsX64()
        val input = largePng()

        val encoded = processUploadPng(input, "assets/example/textures/test.png")

        assertTrue(AvifCodec.isAvif(encoded))
    }

    private fun largePng(): ByteArray {
        val image = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
        val random = Random(42L)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                image.setRGB(x, y, 0xFF000000.toInt() or random.nextInt(1 shl 24))
            }
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "png", output))
            output.toByteArray().also {
                check(it.size > 50 * 1024)
            }
        }
    }

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
