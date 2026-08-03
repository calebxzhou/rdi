package calebxzau.rdi.mediaproc

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MediaProcNativeRuntimeTest {
    @Test
    fun extractsNativeLibrariesIntoStableBundleDirectory(@TempDir tempDir: Path) {
        val nativeJar = tempDir.resolve("ffmpeg-test-windows-x86_64-gpl.jar").toFile()
        JarOutputStream(nativeJar.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("org/bytedeco/ffmpeg/windows-x86_64-gpl/jniavutil.dll"))
            jar.write(byteArrayOf(1, 2, 3))
            jar.closeEntry()
        }

        val first = MediaProcNativeRuntime.prepare(nativeJar, tempDir.resolve("runtime").toFile()).getOrThrow()
        val second = MediaProcNativeRuntime.prepare(nativeJar, tempDir.resolve("runtime").toFile()).getOrThrow()

        assertEquals(first, second)
        assertContentEquals(byteArrayOf(1, 2, 3), first.resolve("jniavutil.dll").readBytes())
    }
}
