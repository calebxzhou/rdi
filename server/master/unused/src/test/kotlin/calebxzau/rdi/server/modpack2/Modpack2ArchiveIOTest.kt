package calebxzau.rdi.server.modpack2

import calebxzau.rdi.server.modpack.Modpack2ArchiveException
import calebxzau.rdi.server.modpack.Modpack2ArchiveIO
import calebxzau.rdi.server.modpack.Modpack2MergeRoot
import calebxzau.rdi.common.model.Modpack2InheritedRawFile
import calebxzau.rdi.common.model.Modpack2ManifestFormat
import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Modpack2ArchiveIOTest {
    @Test
    fun `shared is overlaid by client and server media is filtered case insensitively`() {
        val directory = createTempDirectory("modpack2-archive-test")
        try {
            val source = directory.resolve("source.tar.zst")
            writeArchive(
                source,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.directory("server"),
                    Entry.file("shared/config/common.txt", "shared"),
                    Entry.file("shared/config/only-shared.txt", "only shared"),
                    Entry.file("shared/assets/keep.dat", "keep"),
                    Entry.file("shared/assets/remove.PNG", "remove"),
                    Entry.file("client/config/common.txt", "client"),
                    Entry.file("server/config/common.txt", "server"),
                    Entry.file("server/media/remove.OgG", "remove"),
                    Entry.file("server/media/keep.dat", "keep")
                )
            )

            val client = directory.resolve("client.tar.zst")
            val server = directory.resolve("server.tar.zst")
            val result = Modpack2ArchiveIO.buildMergedArchives(source.toFile(), client.toFile(), server.toFile())

            assertEquals(13, result.source.entryCount)
            assertTrue(result.source.hasServerRoot)
            assertTrue(result.clientArchiveBytes > 0)
            assertTrue(result.serverArchiveBytes!! > 0)
            val clientEntries = readArchive(client)
            assertEquals("client", clientEntries.getValue("config/common.txt"))
            assertEquals("only shared", clientEntries.getValue("config/only-shared.txt"))
            assertTrue(clientEntries.containsKey("assets/remove.PNG"))
            val serverEntries = readArchive(server)
            assertEquals("server", serverEntries.getValue("config/common.txt"))
            assertFalse(serverEntries.containsKey("media/remove.OgG"))
            assertTrue(serverEntries.containsKey("media/keep.dat"))
            assertFalse(serverEntries.keys.any { it.startsWith("shared/") || it.startsWith("server/") })
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun `missing required root and duplicate same root path are rejected`() {
        val directory = createTempDirectory("modpack2-archive-invalid")
        try {
            val missingShared = directory.resolve("missing.tar.zst")
            writeArchive(
                missingShared,
                listOf(Entry.directory("client"), Entry.file("client/a.txt", "a"))
            )
            assertFailsWith<Modpack2ArchiveException> {
                Modpack2ArchiveIO.validateSourceArchive(missingShared.toFile())
            }

            val duplicate = directory.resolve("duplicate.tar.zst")
            writeArchive(
                duplicate,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.file("client/a.txt", "a"),
                    Entry.file("client/a.txt", "b")
                )
            )
            assertFailsWith<Modpack2ArchiveException> {
                Modpack2ArchiveIO.validateSourceArchive(duplicate.toFile())
            }
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun `absolute traversal and file directory conflicts are rejected`() {
        val directory = createTempDirectory("modpack2-archive-invalid")
        try {
            val traversal = directory.resolve("traversal.tar.zst")
            writeArchive(
                traversal,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.file("client/../escape.txt", "escape")
                )
            )
            assertFailsWith<Modpack2ArchiveException> {
                Modpack2ArchiveIO.validateSourceArchive(traversal.toFile())
            }

            val conflict = directory.resolve("conflict.tar.zst")
            writeArchive(
                conflict,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.file("shared/config", "file"),
                    Entry.file("client/config/value.txt", "value")
                )
            )
            assertFailsWith<Modpack2ArchiveException> {
                Modpack2ArchiveIO.validateSourceArchive(conflict.toFile())
            }
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun `generated append materializes declared base server bytes into an independent source`() {
        val directory = createTempDirectory("modpack2-generated-append")
        try {
            val base = directory.resolve("base.tar.zst")
            writeArchive(
                base,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.directory("server"),
                    Entry.file("server/config/base.txt", "base"),
                ),
            )
            val source = directory.resolve("source.tar.zst")
            writeArchive(
                source,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.file("shared/config/new.txt", "new"),
                ),
            )
            val target = directory.resolve("target.tar.zst")
            val result = Modpack2ArchiveIO.materializeGeneratedAppend(
                sourceArchive = source.toFile(),
                baseSourceArchive = base.toFile(),
                inheritedServerFiles = listOf(
                    Modpack2InheritedRawFile(
                        path = "server/config/base.txt",
                        sha1 = sha1("base"),
                    ),
                ),
                targetArchive = target.toFile(),
            )

            assertTrue(result.hasServerRoot)
            val entries = readArchive(target)
            assertEquals("base", entries.getValue("server/config/base.txt"))
            assertEquals("new", entries.getValue("shared/config/new.txt"))
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun `metadata manifest is fixed and excluded from raw and runtime archives`() {
        val directory = createTempDirectory("modpack2-metadata-test")
        try {
            val source = directory.resolve("source.tar.zst")
            writeArchive(
                source,
                listOf(
                    Entry.directory("client"),
                    Entry.directory("shared"),
                    Entry.directory("metadata"),
                    Entry.file("metadata/manifest.json", "{\"name\":\"pack\"}"),
                    Entry.file("client/mod.jar", "mod"),
                ),
            )
            val manifest = Modpack2ArchiveIO.readManifest(source.toFile())
            assertEquals(Modpack2ManifestFormat.CurseForge, manifest.format)
            assertTrue(Modpack2ArchiveIO.rawFileManifest(source.toFile()).none { it.root.name == "Metadata" })
            val client = directory.resolve("client.tar.zst")
            Modpack2ArchiveIO.buildMergedArchives(source.toFile(), client.toFile())
            assertTrue(readArchive(client).keys.none { it.startsWith("metadata/") })
        } finally {
            deleteTree(directory)
        }
    }

    @Test
    fun `nested and duplicate metadata manifests are rejected`() {
        val directory = createTempDirectory("modpack2-metadata-invalid")
        try {
            listOf(
                listOf(
                    Entry.directory("client"), Entry.directory("shared"), Entry.directory("metadata"),
                    Entry.file("metadata/manifest.json", "{}"), Entry.file("metadata/nested/extra.txt", "x"),
                ),
                listOf(
                    Entry.directory("client"), Entry.directory("shared"), Entry.directory("metadata"),
                    Entry.file("metadata/manifest.json", "{}"), Entry.file("metadata/modrinth.index.json", "{}"),
                ),
            ).forEachIndexed { index, entries ->
                val source = directory.resolve("invalid-$index.tar.zst")
                writeArchive(source, entries)
                assertFailsWith<Modpack2ArchiveException> {
                    Modpack2ArchiveIO.validateSourceArchive(source.toFile())
                }
            }
        } finally {
            deleteTree(directory)
        }
    }

    private data class Entry(val path: String, val bytes: ByteArray?, val directory: Boolean) {
        companion object {
            fun directory(path: String) = Entry(path, null, true)
            fun file(path: String, text: String) = Entry(path, text.toByteArray(), false)
        }
    }

    private fun writeArchive(path: Path, entries: List<Entry>) {
        val archiveEntries = if (entries.any { it.path == "metadata" || it.path.startsWith("metadata/") }) {
            entries
        } else {
            entries + listOf(
                Entry.directory("metadata"),
                Entry.file("metadata/manifest.json", "{\"name\":\"test\"}"),
            )
        }
        Files.newOutputStream(path).use { raw ->
            TarArchiveOutputStream(ZstdOutputStream(raw, 9)).use { output ->
                output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                archiveEntries.forEach { source ->
                    val entry = TarArchiveEntry(if (source.directory) "${source.path}/" else source.path)
                    if (!source.directory) entry.size = source.bytes!!.size.toLong()
                    output.putArchiveEntry(entry)
                    if (!source.directory) output.write(source.bytes!!)
                    output.closeArchiveEntry()
                }
            }
        }
    }

    private fun readArchive(path: Path): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Files.newInputStream(path).use { raw ->
            TarArchiveInputStream(ZstdInputStream(raw)).use { input ->
                while (true) {
                    val entry = input.nextTarEntry ?: break
                    if (!entry.isDirectory) result[entry.name] = input.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }

    private fun sha1(value: String): String = java.security.MessageDigest
        .getInstance("SHA-1")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun deleteTree(path: Path) {
        if (Files.isDirectory(path)) {
            Files.list(path).use { children -> children.forEach(::deleteTree) }
        }
        Files.deleteIfExists(path)
    }
}
