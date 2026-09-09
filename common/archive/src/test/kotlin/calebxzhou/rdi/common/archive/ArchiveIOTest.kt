package calebxzhou.rdi.common.archive

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ArchiveIOTest {
    @Test
    fun `zip extraction decodes legacy chinese filename charset`() {
        val root = Files.createTempDirectory("archive-legacy-zip-test").toFile()
        try {
            val archive = root.resolve("pack.zip")
            ZipArchiveOutputStream(archive).use { zip ->
                zip.setEncoding("GBK")
                zip.setUseLanguageEncodingFlag(false)
                zip.setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy.NEVER)
                zip.setFallbackToUTF8(false)
                zip.putArchiveEntry(ZipArchiveEntry("overrides/配置/说明.txt"))
                zip.write("legacy filename content".toByteArray())
                zip.closeArchiveEntry()
            }

            val target = root.resolve("extracted").apply { mkdirs() }
            extractArchiveToDir(archive, target)

            assertEquals(
                "legacy filename content",
                target.resolve("overrides/配置/说明.txt").readText(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `tar zst extraction streams entries to disk`() {
        val root = Files.createTempDirectory("archive-tar-extract-test").toFile()
        try {
            val archive = root.resolve("pack.tar.zst")
            val payload = ByteArray(1024 * 1024) { index -> (index % 251).toByte() }
            TarZstArchiveWriter(archive).use { writer ->
                writer.addDirectory("overrides")
                writer.addFile("overrides/data.bin", payload)
            }

            val progress = mutableListOf<Pair<Int, String>>()
            val target = root.resolve("extracted").apply { mkdirs() }
            extractArchiveToDir(archive, target) { done, total, path ->
                progress += total to "$done:$path"
            }

            assertEquals(listOf(1 to "1:overrides/data.bin"), progress)
            assertContentEquals(payload, target.resolve("overrides/data.bin").readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `zip extraction streams large and chinese entries to disk`() {
        val root = Files.createTempDirectory("archive-extract-test").toFile()
        try {
            val archive = root.resolve("pack.zip")
            val largeSize = 64L * 1024 * 1024
            val chunk = ByteArray(1024 * 1024) { index -> (index % 251).toByte() }
            val expectedDigest = MessageDigest.getInstance("SHA-256")
            archive.outputStream().use { fileOutput ->
                ZipOutputStream(fileOutput).use { zip ->
                    zip.putNextEntry(ZipEntry("overrides/"))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("overrides/配置/说明.txt"))
                    zip.write("中文路径内容".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("overrides/large.bin"))
                    var remaining = largeSize
                    while (remaining > 0) {
                        val count = minOf(remaining, chunk.size.toLong()).toInt()
                        zip.write(chunk, 0, count)
                        expectedDigest.update(chunk, 0, count)
                        remaining -= count
                    }
                    zip.closeEntry()
                }
            }

            val progress = mutableListOf<Pair<Int, String>>()
            val target = root.resolve("extracted")
            target.mkdirs()
            extractArchiveToDir(archive, target) { done, total, path ->
                progress += total to "$done:$path"
            }

            assertEquals(2, progress.last().first)
            assertEquals(
                listOf("1:overrides/配置/说明.txt", "2:overrides/large.bin"),
                progress.map { it.second },
            )
            assertEquals("中文路径内容", target.resolve("overrides/配置/说明.txt").readText())
            assertEquals(largeSize, target.resolve("overrides/large.bin").length())
            assertContentEquals(expectedDigest.digest(), sha256(target.resolve("overrides/large.bin")))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest()
    }
}
