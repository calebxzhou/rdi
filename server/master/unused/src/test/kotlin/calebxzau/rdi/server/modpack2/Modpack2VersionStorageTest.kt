package calebxzau.rdi.server.modpack2

import calebxzau.rdi.server.modpack.Modpack2VersionStorage
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Modpack2VersionStorageTest {
    @Test
    fun `version artifacts use modpack2 layout and deleting move can restore or clean`() {
        val directory = createTempDirectory("modpack2-storage-test")
        try {
            val storage = Modpack2VersionStorage(directory.toFile())
            val modpackId = UUID.randomUUID()
            val versionId = UUID.randomUUID()
            val paths = storage.prepare(modpackId, versionId)
            assertEquals(
                directory.resolve("modpack2").resolve(modpackId.toString()).resolve(versionId.toString()),
                paths.directory
            )
            Files.writeString(paths.sourceArchive, "source")
            Files.writeString(paths.clientArchive, "client")

            val moved = assertNotNull(storage.moveToDeleting(modpackId, versionId))
            assertFalse(Files.exists(paths.directory))
            assertTrue(Files.exists(moved.deleting))
            storage.restore(moved)
            assertTrue(Files.exists(paths.directory))
            assertFalse(Files.exists(moved.deleting))

            val movedAgain = assertNotNull(storage.moveToDeleting(modpackId, versionId))
            storage.postCommitCleanup(movedAgain)
            assertFalse(Files.exists(paths.directory))
            assertFalse(Files.exists(movedAgain.deleting))
        } finally {
            deleteTree(directory)
        }
    }

    private fun deleteTree(path: java.nio.file.Path) {
        if (Files.isDirectory(path)) {
            Files.list(path).use { children -> children.forEach(::deleteTree) }
        }
        Files.deleteIfExists(path)
    }
}
