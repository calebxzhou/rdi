package calebxzhou.rdi.master.service

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class ModStorageTest {
    @Test
    fun `download lookup prefers regular mod cache then client cache`() {
        val root = createTempDirectory("mod-storage-test").toFile()
        try {
            val modDir = root.resolve("mods").apply { mkdirs() }
            val clientDir = root.resolve("client-mods").apply { mkdirs() }
            val clientFile = clientDir.resolve("example.jar").apply { writeText("client") }

            assertEquals(clientFile, ModStorage.findDownloadFile("example.jar", modDir, clientDir).getOrThrow())

            val regularFile = modDir.resolve("example.jar").apply { writeText("regular") }
            assertEquals(regularFile, ModStorage.findDownloadFile("example.jar", modDir, clientDir).getOrThrow())
            assertNull(ModStorage.findDownloadFile("missing.jar", modDir, clientDir).getOrThrow())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `mod cache directories must be writable directories and distinct`() {
        val root = createTempDirectory("mod-storage-test").toFile()
        try {
            val modDir = root.resolve("mods")
            val clientDir = root.resolve("client-mods")
            ModStorage.prepareDirectories(modDir, clientDir).getOrThrow()

            assertEquals(true, modDir.isDirectory)
            assertEquals(true, clientDir.isDirectory)
            assertFails { ModStorage.prepareDirectories(modDir, modDir).getOrThrow() }

            val ordinaryFile = root.resolve("ordinary-file").apply { writeText("file") }
            assertFails { ModStorage.prepareDirectories(modDir, ordinaryFile).getOrThrow() }
        } finally {
            root.deleteRecursively()
        }
    }
}
