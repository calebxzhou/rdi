package calebxzhou.rdi.common.archive

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

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
}
