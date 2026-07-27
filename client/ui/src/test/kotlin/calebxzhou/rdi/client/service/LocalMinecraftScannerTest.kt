package calebxzhou.rdi.client.service

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMinecraftScannerTest {
    @Test
    fun `scan finds runtime and launcher instances but ignores source mods directories`() = runBlocking {
        val root = Files.createTempDirectory("local-minecraft-scan")
        val runtime = root.resolve("shared-minecraft").createDirectories()
        runtime.resolve("assets").createDirectories()
        runtime.resolve("libraries").createDirectories()
        runtime.resolve("versions").createDirectories()

        val launcherInstance = root.resolve("Prism/instances/pack/.minecraft").createDirectories()
        launcherInstance.resolve("mods").createDirectories()
        launcherInstance.resolve("instance.cfg").createFile()

        val sourceDirectory = root.resolve("coding/example/mods").createDirectories().parent
        sourceDirectory.resolve("build.gradle.kts").createFile()

        val installations = LocalMinecraftScanner().scan(listOf(root)).getOrThrow()

        assertEquals(2, installations.size)
        assertTrue(installations.any { it.path == runtime && it.runtimeRoot })
        assertTrue(installations.any { it.path == launcherInstance && it.gameInstance })
        assertFalse(installations.any { it.path == sourceDirectory })
    }
}
