package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.listArchiveEntries
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModpackServiceArchiveTest {
    @Test
    fun `client relative path accepts overrides and gtnh only`() {
        assertEqualsPath("config/a", ModpackService.extractClientPackRelativePathForTest("overrides/config/a"))
        assertEqualsPath("gtnh/config/a", ModpackService.extractClientPackRelativePathForTest("gtnh/config/a"))
        assertFalse(ModpackService.extractClientPackRelativePathForTest("server/config/a") != null)
        assertFalse(ModpackService.extractClientPackRelativePathForTest("config/a") != null)
    }

    @Test
    fun `host filters skip client markers rgp assets and world only when requested`() {
        assertTrue(ModpackService.isClientOnlyMarkedModPathForTest("mods/${CLIENT_ONLY_MARK_PREFIX}client.jar"))
        assertFalse(ModpackService.isClientOnlyMarkedModPathForTest("config/${CLIENT_ONLY_MARK_PREFIX}client.jar"))
        assertTrue(ModpackService.shouldSkipHostClientOnlyJarForTest("mods/example-rgp-client.jar"))
        assertFalse(ModpackService.shouldSkipHostClientOnlyJarForTest("mods/example.jar"))
    }

    @Test
    fun `client archive is built as tar zst with filters`() {
        val pack = ModpackServiceTestFixtures.modpack()
        val version = ModpackServiceTestFixtures.version(pack, "client-filter")
        try {
            version.storageDir.mkdirs()
            TarZstArchiveWriter(version.zstdPack).use { writer ->
                writer.addFile("overrides/config/keep.json", byteArrayOf(1))
                writer.addFile("overrides/shaderpacks/ignored.zip", byteArrayOf(2))
                writer.addFile("overrides/world/region/r.0.0.mca", byteArrayOf(3))
                writer.addFile("gtnh/config/keep.cfg", byteArrayOf(4))
                writer.addFile("server/config/not-client.cfg", byteArrayOf(5))
            }
            ModpackService.buildClientPackForTest(version)
            val entries = listArchiveEntries(version.clientZstdPack).map { it.path }.toSet()
            assertTrue("config/keep.json" in entries)
            assertTrue("gtnh/config/keep.cfg" in entries)
            assertFalse(entries.any { it.startsWith("shaderpacks") })
            assertFalse(entries.any { it.endsWith(".mca") })
            assertFalse(entries.any { it.startsWith("server/") })
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `empty client source deletes generated output and zip migrates`() {
        val pack = ModpackServiceTestFixtures.modpack()
        val emptyVersion = ModpackServiceTestFixtures.version(pack, "empty-client")
        val migrationVersion = ModpackServiceTestFixtures.version(pack, "zip-migration")
        try {
            emptyVersion.storageDir.mkdirs()
            TarZstArchiveWriter(emptyVersion.zstdPack).use { it.addFile("server/only.txt", byteArrayOf(1)) }
            ModpackService.buildClientPackForTest(emptyVersion)
            assertFalse(emptyVersion.clientZstdPack.exists())

            migrationVersion.storageDir.mkdirs()
            ZipOutputStream(migrationVersion.zip.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("overrides/config/a.txt"))
                output.write(byteArrayOf(1, 2, 3))
                output.closeEntry()
            }
            ModpackService.upgradeFullPackArchiveForTest(migrationVersion)
            assertTrue(migrationVersion.zstdPack.exists())
            assertFalse(migrationVersion.zip.exists())
            assertTrue(listArchiveEntries(migrationVersion.zstdPack).any { it.path == "overrides/config/a.txt" })
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `archive extraction rejects traversal`() {
        val root = ModpackServiceTestFixtures.tempRoot()
        val target = root.resolve("target")
        val archive = root.resolve("unsafe.tar.zst")
        try {
            TarZstArchiveWriter(archive).use { it.addFile("overrides/../escape.txt", byteArrayOf(1)) }
            assertFailsWith<Exception> { ModpackService.unzipOverridesForTest(archive, target) }
            assertFalse(root.resolve("escape.txt").exists())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    private fun assertEqualsPath(expected: String, actual: String?) {
        kotlin.test.assertEquals(expected, actual)
    }
}
