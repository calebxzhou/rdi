package calebxzau.rdi.server.service.baseworld

import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.util.sha1
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BaseWorldUploadStoreTest {
    @Test
    fun `store startup cleans only direct orphaned part temporaries`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-orphans").toFile()
        try {
            val orphan = root.resolve(".base-world-part-orphan.tmp").apply { writeText("stale") }
            val sentinel = root.resolve("keep.me").apply { writeText("keep") }
            BaseWorldUploadStore(root)
            assertTrue(!orphan.exists())
            assertTrue(sentinel.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `out of order parts are extracted and published staging keeps root files`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-test").toFile()
        try {
            val archive = root.resolve("source.tar.zst")
            TarZstArchiveWriter(archive).use { writer ->
                writer.addDirectory("region")
                writer.addFile("level.dat", byteArrayOf(1, 2, 3))
                writer.addFile("region/r.0.0.mca", byteArrayOf(4, 5, 6, 7))
            }
            val bytes = archive.readBytes()
            val store = BaseWorldUploadStore(root, bytes.size.toLong(), 7, partSize = 7)
            val owner = UUID.randomUUID()
            val world = UUID.randomUUID()
            val session = store.create(owner, world, bytes.size.toLong(), digest(bytes))
            for (index in session.partCount - 1 downTo 0) {
                val start = index * session.partSize
                val end = minOf(bytes.size, start + session.partSize)
                val part = bytes.copyOfRange(start, end)
                store.uploadPart(owner, world, session.id, index, part.size.toLong(), digest(part), ByteReadChannel(part))
            }
            val prepared = prepare(store, owner, world, session.id)
            val extracted = store.extract(prepared)
            assertTrue(extracted.stage.resolve("world.tar.zst").isFile)
            assertEquals(bytes.size.toLong(), extracted.stage.resolve("world.tar.zst").length())
            assertEquals(7, extracted.size)
            extracted.stage.deleteRecursively()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `path traversal archive is rejected without publishing`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-invalid").toFile()
        try {
            val archive = root.resolve("source.tar.zst")
            TarZstArchiveWriter(archive).use { writer ->
                writer.addFile("../evil", byteArrayOf(1))
                writer.addFile("level.dat", byteArrayOf(1))
            }
            val bytes = archive.readBytes()
            val store = BaseWorldUploadStore(root, bytes.size.toLong(), 100, partSize = bytes.size)
            val owner = UUID.randomUUID()
            val world = UUID.randomUUID()
            val session = store.create(owner, world, bytes.size.toLong(), digest(bytes))
            store.uploadPart(owner, world, session.id, 0, bytes.size.toLong(), digest(bytes), ByteReadChannel(bytes))
            val prepared = prepare(store, owner, world, session.id)
            assertFailsWith<Throwable> { store.extract(prepared) }
            assertTrue(!root.resolve("evil").exists())
            prepared.archive.delete()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `session metadata reloads and failed retry preserves valid part`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-restart").toFile()
        try {
            val store = BaseWorldUploadStore(root, 8, 8, partSize = 4)
            val owner = UUID.randomUUID()
            val world = UUID.randomUUID()
            val bytes = byteArrayOf(1, 2, 3, 4)
            val session = store.create(owner, world, 4, digest(bytes))
            store.uploadPart(owner, world, session.id, 0, 4, digest(bytes), ByteReadChannel(bytes))
            val restarted = BaseWorldUploadStore(root, 8, 8, partSize = 4)
            assertEquals(listOf(0), restarted.status(owner, world, session.id).uploadedParts)
            assertFailsWith<Throwable> {
                restarted.uploadPart(owner, world, session.id, 0, 4, digest(byteArrayOf(9, 9, 9, 9)), ByteReadChannel(byteArrayOf(9, 9, 9, 9)))
            }
            assertEquals(listOf(0), restarted.status(owner, world, session.id).uploadedParts)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `compressed and extracted limits are enforced`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-limits").toFile()
        try {
            val archive = root.resolve("source.tar.zst")
            TarZstArchiveWriter(archive).use { it.addFile("level.dat", byteArrayOf(1, 2, 3, 4, 5)) }
            val bytes = archive.readBytes()
            val owner = UUID.randomUUID()
            val world = UUID.randomUUID()
            assertFailsWith<Throwable> { BaseWorldUploadStore(root, bytes.size - 1L, 100, partSize = 4).create(owner, world, bytes.size.toLong(), digest(bytes)) }
            val exact = BaseWorldUploadStore(root, bytes.size.toLong(), 5, partSize = bytes.size)
            val session = exact.create(owner, world, bytes.size.toLong(), digest(bytes))
            exact.uploadPart(owner, world, session.id, 0, bytes.size.toLong(), digest(bytes), ByteReadChannel(bytes))
            val prepared = prepare(exact, owner, world, session.id)
            val extracted = exact.extract(prepared)
            assertEquals(5, extracted.size)
            extracted.stage.deleteRecursively()
            exact.cleanupSession(prepared)
            val tooSmall = BaseWorldUploadStore(root, bytes.size.toLong(), 4, partSize = bytes.size)
            val secondWorld = UUID.randomUUID()
            val second = tooSmall.create(owner, secondWorld, bytes.size.toLong(), digest(bytes))
            tooSmall.uploadPart(owner, secondWorld, second.id, 0, bytes.size.toLong(), digest(bytes), ByteReadChannel(bytes))
            val secondPrepared = prepare(tooSmall, owner, secondWorld, second.id)
            assertFailsWith<Throwable> { tooSmall.extract(secondPrepared) }
            secondPrepared.archive.delete()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing parts and whole digest failures do not publish and cancel removes session`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-errors").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val owner = UUID.randomUUID()
            val world = UUID.randomUUID()
            val store = BaseWorldUploadStore(root, 8, 8, partSize = 2)
            val session = store.create(owner, world, bytes.size.toLong(), digest(bytes))
            assertFailsWith<Throwable> { store.openForCompletion(owner, world, session.id) }
            assertFailsWith<Throwable> {
                store.uploadPart(owner, world, session.id, 0, 2, digest(byteArrayOf(9, 9)), ByteReadChannel(byteArrayOf(1, 2)))
            }
            store.uploadPart(owner, world, session.id, 0, 2, digest(bytes.copyOfRange(0, 2)), ByteReadChannel(bytes.copyOfRange(0, 2)))
            val otherWorld = UUID.randomUUID()
            assertFailsWith<Throwable> { store.status(owner, otherWorld, session.id) }
            store.cancel(owner, world, session.id)
            assertFailsWith<Throwable> { store.status(owner, world, session.id) }

            val mismatchWorld = UUID.randomUUID()
            val mismatch = store.create(owner, mismatchWorld, bytes.size.toLong(), "0000000000000000000000000000000000000000")
            store.uploadPart(owner, mismatchWorld, mismatch.id, 0, 2, digest(bytes.copyOfRange(0, 2)), ByteReadChannel(bytes.copyOfRange(0, 2)))
            store.uploadPart(owner, mismatchWorld, mismatch.id, 1, 2, digest(bytes.copyOfRange(2, 4)), ByteReadChannel(bytes.copyOfRange(2, 4)))
            store.acceptForCompletion(owner, mismatchWorld, mismatch.id)
            store.markProcessing(owner, mismatchWorld, mismatch.id)
            assertFailsWith<Throwable> { store.openForCompletion(owner, mismatchWorld, mismatch.id) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `archive must contain nonempty root level dat and malformed input is rejected`() = runTest {
        val root = Files.createTempDirectory("base-world-upload-archive-errors").toFile()
        try {
            val missingLevel = root.resolve("missing.tar.zst")
            TarZstArchiveWriter(missingLevel).use { it.addFile("region/file", byteArrayOf(1)) }
            val malformed = root.resolve("malformed.tar.zst").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            for (archive in listOf(missingLevel, malformed)) {
                val bytes = archive.readBytes()
                val world = UUID.randomUUID()
                val owner = UUID.randomUUID()
                val store = BaseWorldUploadStore(root, bytes.size.toLong(), 100, partSize = bytes.size)
                val session = store.create(owner, world, bytes.size.toLong(), digest(bytes))
                store.uploadPart(owner, world, session.id, 0, bytes.size.toLong(), digest(bytes), ByteReadChannel(bytes))
                val prepared = prepare(store, owner, world, session.id)
                assertFailsWith<Throwable> { store.extract(prepared) }
                prepared.archive.delete()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun digest(bytes: ByteArray): String = bytes.sha1

    private suspend fun prepare(store: BaseWorldUploadStore, owner: UUID, world: UUID, id: UUID): BaseWorldUploadStore.PreparedUpload {
        store.acceptForCompletion(owner, world, id)
        store.markProcessing(owner, world, id)
        return store.openForCompletion(owner, world, id)
    }

}
