package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.std.sha1
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McCoreUpdaterTest {
    @Test
    fun `core replacement preserves hard links`() {
        val root = Files.createTempDirectory("core-hard-link").toFile()
        try {
            val cache = root.resolve("core.jar").apply { writeText("old") }
            val linked = root.resolve("linked.jar")
            Files.createLink(linked.toPath(), cache.toPath())
            val downloaded = root.resolve("downloaded.jar").apply { writeText("new") }

            McCoreUpdater.replaceCoreContents(downloaded, cache, downloaded.sha1).getOrThrow()

            assertTrue(Files.isSameFile(cache.toPath(), linked.toPath()))
            assertEquals("new", linked.readText())
            assertFalse(downloaded.exists())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `failed core verification restores every hard link`() {
        val root = Files.createTempDirectory("core-rollback").toFile()
        try {
            val cache = root.resolve("core.jar").apply { writeText("old") }
            val linked = root.resolve("linked.jar")
            Files.createLink(linked.toPath(), cache.toPath())
            val downloaded = root.resolve("downloaded.jar").apply { writeText("new") }

            assertTrue(McCoreUpdater.replaceCoreContents(downloaded, cache, "0".repeat(40)).isFailure)
            assertEquals("old", cache.readText())
            assertEquals("old", linked.readText())
            assertFalse(root.resolve("core.jar.replacing-backup").exists())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }
}
