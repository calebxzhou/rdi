package calebxzhou.rdi.client.service

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaProcGameClasspathTest {
    @Test
    fun resolvesMinimalWindowsRuntime() {
        assumeWindowsX64()
        val files = MediaProcGameClasspath.resolve().getOrThrow()

        assertEquals(5, files.size)
        assertTrue(files.any { it.name == "javacv-1.5.13.jar" })
        assertTrue(files.any { it.name == "javacpp-1.5.13.jar" })
        assertTrue(files.any { it.name == "ffmpeg-8.0.1-1.5.13.jar" })
        assertTrue(files.any { it.name == "ffmpeg-8.0.1-1.5.13-windows-x86_64-gpl.jar" })
    }

    @Test
    fun appendsMediaRuntimeToBootstrapIgnoreList() {
        val argument = "-DignoreList=client-extra,neoforge.jar"
        val mediaClasspath = listOf(
            File("mediaproc/build/classes/kotlin/main"),
            File("javacv-1.5.13.jar"),
            File("javacpp-1.5.13.jar"),
            File("ffmpeg-8.0.1-1.5.13.jar"),
            File("ffmpeg-8.0.1-1.5.13-windows-x86_64-gpl.jar")
        )

        val resolved = argument.appendBootstrapIgnoreFiles(mediaClasspath)

        assertEquals(
            "-DignoreList=client-extra,neoforge.jar,main,javacv-1.5.13.jar,javacpp-1.5.13.jar," +
                "ffmpeg-8.0.1-1.5.13.jar,ffmpeg-8.0.1-1.5.13-windows-x86_64-gpl.jar",
            resolved
        )
    }

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
