package calebxzhou.rdi.master.service

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModpackUploadTempStorageTest {
    @Test
    fun `upload files are created in dedicated directory and stale files are cleaned`() {
        val root = createTempDirectory("modpack-upload-storage-test").toFile()
        try {
            val uploadDir = root.resolve(".upload-tmp")
            val storage = ModpackUploadTempStorage(uploadDir)
            val firstUpload = storage.createTempFile().getOrThrow()
            val secondUpload = storage.createTempFile().getOrThrow()
            val unrelated = uploadDir.resolve("keep.txt").apply { writeText("keep") }

            assertEquals(uploadDir.canonicalFile, firstUpload.parentFile.canonicalFile)
            assertEquals(2, storage.cleanupStaleFiles().getOrThrow())
            assertFalse(firstUpload.exists())
            assertFalse(secondUpload.exists())
            assertTrue(unrelated.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
