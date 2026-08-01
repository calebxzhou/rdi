package calebxzau.rdi.client.ui

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageDecodingTest {
    @Test
    fun decodesAvifThroughFfmpegIntoSkiaBitmap() {
        assumeWindowsX64()
        val input = javaClass.getResourceAsStream("/assets/bg.avif")!!.use { it.readBytes() }

        val bitmap = decodeImageBitmap(input).getOrThrow()

        assertEquals(1280, bitmap.width)
        assertEquals(720, bitmap.height)
    }

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
