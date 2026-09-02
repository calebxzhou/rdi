package calebxzau.rdi.mcinstall

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McDirectoryEnumeratorTest {
    @Test
    fun `native failure falls back to nio enumerator`() {
        val expected = McDirectoryEntry(Path.of("fallback"), false)
        val enumerator = WindowsMcDirectoryEnumerator(
            native = McDirectoryEnumerator { Result.failure(IllegalStateException("native failed")) },
            fallback = McDirectoryEnumerator { Result.success(listOf(expected)) }
        )

        assertEquals(listOf(expected), enumerator.enumerate(Path.of("root")).getOrThrow())
    }

    @Test
    fun `ordinary reparse points are not descended`() = runBlocking {
        val root = Path.of("drive-root").toAbsolutePath().normalize()
        val reparsePoint = root.resolve("junction")
        var descended = false
        val scanner = LocalMcScanner(
            enumerator = McDirectoryEnumerator { directory ->
                if (directory == root) {
                    Result.success(listOf(McDirectoryEntry(reparsePoint, true)))
                } else {
                    descended = true
                    Result.success(emptyList())
                }
            },
            validator = DefaultMcInstallationValidator { true }
        )

        assertTrue(scanner.scan(listOf(root)).isSuccess)
        assertFalse(descended)
    }

    @Test
    fun `enumeration is globally limited to twelve operations`() = runBlocking {
        val roots = (0 until 4).map { Path.of("drive-$it").toAbsolutePath().normalize() }
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val enumerator = McDirectoryEnumerator { directory ->
            val current = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, current) }
            Thread.sleep(10)
            active.decrementAndGet()
            if (directory in roots) {
                Result.success((0 until 8).map { index ->
                    McDirectoryEntry(directory.resolve("child-$index"), false)
                })
            } else {
                Result.success(emptyList())
            }
        }

        LocalMcScanner(
            enumerator = enumerator,
            validator = DefaultMcInstallationValidator { true }
        ).scan(roots).getOrThrow()

        assertTrue(maximum.get() <= 12)
    }

    @Test
    fun `native windows enumerator returns child directories`() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) return
        val root = Files.createTempDirectory("minecraft-enumerator")
        root.resolve("child").createDirectories()
        Files.writeString(root.resolve("file.txt"), "file")

        val entries = WindowsNativeMcDirectoryEnumerator().enumerate(root).getOrThrow()

        assertEquals(listOf(root.resolve("child")), entries.map { it.path })
        assertFalse(entries.single().isReparsePoint)
    }
}
