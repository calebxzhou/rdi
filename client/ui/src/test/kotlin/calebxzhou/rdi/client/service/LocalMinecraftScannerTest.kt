package calebxzhou.rdi.client.service

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMinecraftScannerTest {
    @Test
    fun `scan finds nested runtime roots and returns resolved paths`() = runBlocking {
        val root = Files.createTempDirectory("local-minecraft-scan")
        val first = createRuntimeRoot(root.resolve("nested/first/.minecraft"))
        val second = createRuntimeRoot(root.resolve("nested/second/.minecraft"))
        val scanner = LocalMinecraftScanner(
            enumerator = NioMinecraftDirectoryEnumerator(),
            validator = testValidator()
        )

        val installations = scanner.scan(listOf(root)).getOrThrow()

        assertEquals(setOf(first.toRealPath(), second.toRealPath()), installations.toSet())
    }

    @Test
    fun `scanner stops descending at every minecraft candidate`() = runBlocking {
        val root = Files.createTempDirectory("local-minecraft-scan")
        val outer = createRuntimeRoot(root.resolve("outer/.minecraft"))
        createRuntimeRoot(outer.resolve("nested/.minecraft"))
        val scanner = LocalMinecraftScanner(
            enumerator = NioMinecraftDirectoryEnumerator(),
            validator = testValidator()
        )

        val installations = scanner.scan(listOf(root)).getOrThrow()

        assertEquals(listOf(outer.toRealPath()), installations)
    }

    @Test
    fun `child enumeration failure does not fail the drive`() = runBlocking {
        val root = Path.of("drive-root").toAbsolutePath().normalize()
        val child = root.resolve("blocked")
        val enumerator = MinecraftDirectoryEnumerator { directory ->
            when (directory) {
                root -> Result.success(listOf(MinecraftDirectoryEntry(child, false)))
                child -> Result.failure(IllegalStateException("blocked"))
                else -> Result.success(emptyList())
            }
        }

        val result = LocalMinecraftScanner(
            enumerator = enumerator,
            validator = testValidator()
        ).scan(listOf(root))

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun `drive root enumeration failure fails the scan`() = runBlocking {
        val root = Path.of("drive-root")
        val scanner = LocalMinecraftScanner(
            enumerator = MinecraftDirectoryEnumerator { Result.failure(IllegalStateException("root failed")) },
            validator = testValidator()
        )

        val result = scanner.scan(listOf(root))

        assertTrue(result.isFailure)
    }

    @Test
    fun `duplicate resolved candidates are emitted once`() = runBlocking {
        val root = Path.of("drive-root").toAbsolutePath().normalize()
        val candidate = root.resolve(".minecraft")
        val enumerator = MinecraftDirectoryEnumerator { directory ->
            if (directory == root) {
                Result.success(
                    listOf(
                        MinecraftDirectoryEntry(candidate, false),
                        MinecraftDirectoryEntry(candidate, false)
                    )
                )
            } else {
                Result.success(emptyList())
            }
        }
        val found = mutableListOf<Path>()

        LocalMinecraftScanner(
            enumerator = enumerator,
            validator = object : MinecraftInstallationValidator {
                override fun validateDiscoveredCandidate(candidate: Path, fixedDriveRoots: List<Path>) =
                    MinecraftInstallationValidation.Valid(Path.of("C:/Minecraft/.minecraft"))

                override fun validateStoredRealPath(realPath: Path, fixedDriveRoots: List<Path>) =
                    MinecraftInstallationValidation.Valid(realPath)
            }
        ).scan(listOf(root)) { found.add(it) }.getOrThrow()

        assertEquals(1, found.size)
    }

    private fun createRuntimeRoot(path: Path): Path {
        path.resolve("assets").createDirectories()
        path.resolve("libraries").createDirectories()
        path.resolve("versions").createDirectories()
        return path
    }

    private fun testValidator() = DefaultMinecraftInstallationValidator { true }
}
