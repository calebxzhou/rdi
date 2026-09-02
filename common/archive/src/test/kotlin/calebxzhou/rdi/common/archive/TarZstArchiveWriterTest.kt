package calebxzhou.rdi.common.archive

import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TarZstArchiveWriterTest {
    @Test
    fun `streams file into tar zst`() {
        val directory = createTempDirectory("tar-zst-writer-test").toFile()
        try {
            val source = directory.resolve("source.bin").apply { writeBytes(ByteArray(1024) { it.toByte() }) }
            val archive = directory.resolve("pack.tar.zst")
            TarZstArchiveWriter(archive).use { it.addFile("data/source.bin", source) }

            val entry = mutableListOf<ArchiveEntryData>()
            forEachArchiveEntry(archive, entry::add)
            assertEquals("data/source.bin", entry.single().path)
            assertContentEquals(source.readBytes(), entry.single().bytes)
        } finally {
            directory.walkBottomUp().forEach { Files.deleteIfExists(it.toPath()) }
        }
    }

    @Test
    fun `first entry can be read without visiting payload`() {
        val directory = createTempDirectory("tar-zst-first-test").toFile()
        try {
            val archive = directory.resolve("pack.tar.zst")
            TarZstArchiveWriter(archive).use {
                it.addFile("manifest", "manifest".toByteArray())
                it.addFile("payload", ByteArray(1024 * 1024) { 7 })
            }
            var visited = ""
            assertTrue(readFirstTarZstEntry(archive) { entry, input ->
                visited = entry.path
                input.readNBytes(entry.size.toInt())
            })
            assertEquals("manifest", visited)
        } finally {
            directory.walkBottomUp().forEach { Files.deleteIfExists(it.toPath()) }
        }
    }

    @Test
    fun `streaming writer invokes cancellation callback between buffers`() {
        val directory = createTempDirectory("tar-zst-streaming-cancel-test").toFile()
        try {
            val archive = directory.resolve("pack.tar.zst")
            val payload = ByteArray(DEFAULT_BUFFER_SIZE * 3) { it.toByte() }
            var callbacks = 0
            assertFailsWith<IllegalStateException> {
                TarZstArchiveWriter(archive).use {
                    it.addFileStreaming(
                        "data.bin",
                        ByteArrayInputStream(payload),
                        payload.size.toLong(),
                        beforeChunk = {
                            callbacks++
                            if (callbacks == 2) error("cancel")
                        },
                    )
                }
            }
            assertEquals(2, callbacks)
            val moved = directory.resolve("moved.tar.zst")
            Files.move(archive.toPath(), moved.toPath())
            assertTrue(Files.exists(moved.toPath()))
        } finally {
            directory.walkBottomUp().forEach { Files.deleteIfExists(it.toPath()) }
        }
    }

}
