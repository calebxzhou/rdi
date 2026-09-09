package calebxzau.rdi.client.packproc

import calebxzau.rdi.client.modcatalog.ModCatalog
import calebxzau.rdi.client.modcatalog.CatalogModMetadata
import calebxzau.rdi.client.modcatalog.CatalogSlugRef
import calebxzhou.rdi.common.archive.forEachArchiveEntry
import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackModelsTest {
    @Test
    fun `failed local archive preparation removes temporary pack directory`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-extract-failure").toFile()
        try {
            val archive = root.resolve("invalid.zip")
            archive.outputStream().use { fileOutput ->
                ZipOutputStream(fileOutput).use { zip ->
                    zip.putNextEntry(ZipEntry("modrinth.index.json"))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("overrides/"))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("../escape.txt"))
                    zip.write("must not escape".toByteArray())
                    zip.closeEntry()
                }
            }
            val workDir = root.resolve("work")
            val result = ModpackProcessor(PackProcessingPaths(workDir)).loadLocalModpack(
                modCatalog = noCallModCatalog(),
                file = archive,
                onProgress = {},
            )

            assertTrue(result.isFailure)
            assertTrue(workDir.listFiles().orEmpty().none { it.name.startsWith("pack-") })
            assertFalse(workDir.resolve("escape.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `client mp3 files are replaced in regular nested and resourcepack entries`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-mp3-upload").toFile()
        try {
            root.resolve("sounds/source.MP3").also { it.parentFile.mkdirs() }
                .writeBytes("original top-level audio".toByteArray())
            writeZip(
                root.resolve("nested.zip"),
                mapOf("sounds/nested.mp3" to "original zip audio".toByteArray())
            )
            writeZip(
                root.resolve("mods/example.jar"),
                mapOf("assets/example/nested.MP3" to "original jar audio".toByteArray())
            )
            writeZip(
                root.resolve("resourcepacks/music-pack.zip"),
                mapOf(
                    "assets/music/lower.mp3" to "original resourcepack zip audio".toByteArray(),
                    "assets/music/upper.MP3" to "original resourcepack zip audio".toByteArray(),
                    "assets/music/texture.bin" to byteArrayOf(0x01, 0x02, 0x03, 0x04),
                    "assets/music/original.ogg" to byteArrayOf(0x4f, 0x67, 0x67, 0x53, 0x55, 0x46, 0x46, 0x49, 0x58)
                )
            )
            root.resolve("resourcepacks/test/assets/test.mp3").also { it.parentFile.mkdirs() }
                .writeBytes("original resourcepack audio".toByteArray())

            val processor = ModpackProcessor(PackProcessingPaths(root.resolve("work")))
            val archive = processor.buildUploadArchive(root, "mp3-upload")
            val archiveFiles = mutableMapOf<String, ByteArray>()
            forEachArchiveEntry(archive) { entry ->
                if (!entry.isDirectory) archiveFiles[entry.path] = entry.bytes!!
            }
            val placeholder = javaClass.classLoader.getResourceAsStream("assets/empty.mp3")!!.use { it.readBytes() }

            assertContentEquals(placeholder, archiveFiles["sounds/source.MP3"])
            assertContentEquals(placeholder, archiveFiles["resourcepacks/test/assets/test.mp3"])

            assertNestedZipEntryEquals(archiveFiles["nested.zip"]!!, "sounds/nested.mp3", placeholder)
            assertNestedZipEntryEquals(archiveFiles["mods/example.jar"]!!, "assets/example/nested.MP3", placeholder)
            assertNestedZipEntryEquals(archiveFiles["resourcepacks/music-pack.zip"]!!, "assets/music/lower.mp3", placeholder)
            assertNestedZipEntryEquals(archiveFiles["resourcepacks/music-pack.zip"]!!, "assets/music/upper.MP3", placeholder)
            assertNestedZipEntryEquals(
                archiveFiles["resourcepacks/music-pack.zip"]!!,
                "assets/music/texture.bin",
                byteArrayOf(0x01, 0x02, 0x03, 0x04)
            )
            assertNestedZipEntryEquals(
                archiveFiles["resourcepacks/music-pack.zip"]!!,
                "assets/music/original.ogg",
                byteArrayOf(0x4f, 0x67, 0x67, 0x53, 0x55, 0x46, 0x46, 0x49, 0x58)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `mca files are filtered except exact ftbteambases segments`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-mca-upload").toFile()
        try {
            root.resolve("region/r.0.0.mca").also { it.parentFile.mkdirs() }.writeBytes(byteArrayOf(1))
            root.resolve("myftbteambasescopy/r.0.0.mca").also { it.parentFile.mkdirs() }.writeBytes(byteArrayOf(2))
            root.resolve("FTBTeamBases/region/r.0.0.MCA").also { it.parentFile.mkdirs() }.writeBytes(byteArrayOf(3))
            writeZip(
                root.resolve("mods/example.jar"),
                mapOf(
                    "region/nested.mca" to byteArrayOf(4),
                    "foo/ftbteambases/kept.mca" to byteArrayOf(5),
                    "assets/example.txt" to byteArrayOf(6),
                )
            )
            writeZip(
                root.resolve("resourcepacks/example.zip"),
                mapOf(
                    "region/resourcepack.mca" to byteArrayOf(7),
                    "ftbteambases/resourcepack-kept.mca" to byteArrayOf(8),
                    "assets/texture.bin" to byteArrayOf(9),
                )
            )
            writeZip(
                root.resolve("data/eligible.zip"),
                mapOf("region/eligible.mca" to byteArrayOf(10))
            )
            writeZip(
                root.resolve("data/eligible.jar"),
                mapOf("region/eligible-jar.mca" to byteArrayOf(11))
            )

            val archive = ModpackProcessor(PackProcessingPaths(root.resolve("work")))
                .buildUploadArchive(root, "mca-upload")
            val archiveFiles = mutableMapOf<String, ByteArray>()
            forEachArchiveEntry(archive) { entry ->
                if (!entry.isDirectory) archiveFiles[entry.path] = entry.bytes!!
            }

            assertFalse(archiveFiles.containsKey("region/r.0.0.mca"))
            assertFalse(archiveFiles.containsKey("myftbteambasescopy/r.0.0.mca"))
            assertContentEquals(byteArrayOf(3), archiveFiles["FTBTeamBases/region/r.0.0.MCA"])
            assertNestedZipEntryMissing(archiveFiles["mods/example.jar"]!!, "region/nested.mca")
            assertNestedZipEntryEquals(archiveFiles["mods/example.jar"]!!, "foo/ftbteambases/kept.mca", byteArrayOf(5))
            assertNestedZipEntryMissing(archiveFiles["resourcepacks/example.zip"]!!, "region/resourcepack.mca")
            assertNestedZipEntryEquals(
                archiveFiles["resourcepacks/example.zip"]!!,
                "ftbteambases/resourcepack-kept.mca",
                byteArrayOf(8)
            )
            assertNestedZipEntryEquals(archiveFiles["resourcepacks/example.zip"]!!, "assets/texture.bin", byteArrayOf(9))
            assertNestedZipEntryMissing(archiveFiles["data/eligible.zip"]!!, "region/eligible.mca")
            assertNestedZipEntryMissing(archiveFiles["data/eligible.jar"]!!, "region/eligible-jar.mca")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `local preparation detects excluded mca files after extraction`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-mca-detection").toFile()
        try {
            val archive = root.resolve("mca-detection.zip")
            writeZip(
                archive,
                mapOf(
                    "modrinth.index.json" to modrinthIndexBytes(),
                    "overrides/FTBTeamBases/region/kept.mca" to byteArrayOf(2),
                    "overrides/data/eligible.zip" to zipBytes(
                        mapOf("region/eligible.mca" to byteArrayOf(4))
                    ),
                    "overrides/data/eligible.jar" to zipBytes(
                        mapOf("region/eligible-jar.mca" to byteArrayOf(5))
                    ),
                )
            )
            val loaded = ModpackProcessor(PackProcessingPaths(root.resolve("work")))
                .loadLocalModpack(noCallModCatalog(), archive, onProgress = {})
                .getOrThrow()
            assertTrue(loaded.containsExcludedMcaFiles)

            val onlyKeptArchive = root.resolve("only-kept.zip")
            writeZip(
                onlyKeptArchive,
                mapOf(
                    "modrinth.index.json" to modrinthIndexBytes(),
                    "overrides/ftbteambases/region/kept.mca" to byteArrayOf(3),
                )
            )
            val onlyKept = ModpackProcessor(PackProcessingPaths(root.resolve("work-kept")))
                .loadLocalModpack(noCallModCatalog(), onlyKeptArchive, onProgress = {})
                .getOrThrow()
            assertFalse(onlyKept.containsExcludedMcaFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `local preparation skips malformed shaderpack archives during mca detection`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-skipped-archive").toFile()
        try {
            val archive = root.resolve("skipped-archive.zip")
            writeZip(
                archive,
                mapOf(
                    "modrinth.index.json" to modrinthIndexBytes(),
                    "overrides/shaderpacks/broken.zip" to "not a zip".toByteArray(),
                )
            )

            val loaded = ModpackProcessor(PackProcessingPaths(root.resolve("work")))
                .loadLocalModpack(noCallModCatalog(), archive, onProgress = {})
                .getOrThrow()

            assertFalse(loaded.containsExcludedMcaFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `server extra mca files and direct archive entries are filtered`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-server-mca").toFile()
        try {
            val extraRoot = root.resolve("server-files").also { it.mkdirs() }
            val excluded = extraRoot.resolve("world/region/r.0.0.mca").also { it.parentFile.mkdirs() }
            excluded.writeBytes(byteArrayOf(1))
            val kept = extraRoot.resolve("ftbteambases/region/r.0.0.mca").also { it.parentFile.mkdirs() }
            kept.writeBytes(byteArrayOf(2))
            val nested = extraRoot.resolve("data.zip")
            writeZip(
                nested,
                mapOf(
                    "world/region/nested.mca" to byteArrayOf(3),
                    "ftbteambases/kept.mca" to byteArrayOf(4),
                    "sounds/server.mp3" to byteArrayOf(7, 8),
                    "cache/kept.dat" to byteArrayOf(9, 10),
                )
            )

            val archive = ModpackProcessor(PackProcessingPaths(root.resolve("work"))).buildUploadArchive(
                rootDir = root.resolve("empty").also { it.mkdirs() },
                baseName = "server-mca-upload",
                serverExtraFiles = listOf(
                    ServerExtraFile(excluded, "world/region/r.0.0.mca"),
                    ServerExtraFile(kept, "ftbteambases/region/r.0.0.mca"),
                    ServerExtraFile(nested, "data.zip"),
                ),
            )
            val archiveFiles = mutableMapOf<String, ByteArray>()
            forEachArchiveEntry(archive) { entry ->
                if (!entry.isDirectory) archiveFiles[entry.path] = entry.bytes!!
            }
            assertFalse(archiveFiles.containsKey("server/world/region/r.0.0.mca"))
            assertContentEquals(byteArrayOf(2), archiveFiles["server/ftbteambases/region/r.0.0.mca"])
            assertNestedZipEntryMissing(archiveFiles["server/data.zip"]!!, "world/region/nested.mca")
            assertNestedZipEntryEquals(archiveFiles["server/data.zip"]!!, "ftbteambases/kept.mca", byteArrayOf(4))
            assertNestedZipEntryEquals(archiveFiles["server/data.zip"]!!, "sounds/server.mp3", byteArrayOf(7, 8))
            assertNestedZipEntryEquals(archiveFiles["server/data.zip"]!!, "cache/kept.dat", byteArrayOf(9, 10))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `server extra media extensions are excluded case insensitively`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-server-media").toFile()
        try {
            val serverMods = root.resolve("mods").also { it.mkdirs() }
            val stagedClient = root.resolve("staged/client.jar")
                .also { it.parentFile.mkdirs() }
            writeModJar(stagedClient, "shared")
            writeModJar(serverMods.resolve("server-shared.jar"), "shared")

            val mediaExtensions = listOf(
                "ogg", "wav", "mp3", "flac", "aac", "m4a", "opus", "wma",
                "mp4", "mov", "avi", "mkv", "webm", "m4v", "mpeg", "mpg", "flv", "wmv",
                "png", "jpg", "jpeg", "webp", "gif", "bmp", "tif", "tiff", "avif", "ico", "svg",
                "psd"
            )
            mediaExtensions.forEachIndexed { index, extension ->
                val suffix = if (index == 0) extension.uppercase() else extension
                root.resolve("media/nested/file.$suffix").also { it.parentFile.mkdirs() }
                    .writeBytes(byteArrayOf(1, 2, 3))
            }
            root.resolve("server.json").writeText("{}")
            root.resolve("server.toml").writeText("enabled = true")
            writeZip(
                root.resolve("server-data.zip"),
                mapOf("world/region/server.mca" to byteArrayOf(11))
            )

            val loaded = ModpackProcessor(
                PackProcessingPaths(root.resolve("work"))
            ).loadServerPack(
                file = root,
                clientMods = listOf(
                    Mod(
                        platform = "mr",
                        projectId = "project",
                        slug = "shared",
                        fileId = "file",
                        hash = "0123456789012345678901234567890123456789",
                    )
                ),
                clientModSources = mapOf(
                    Mod(
                        platform = "mr",
                        projectId = "project",
                        slug = "shared",
                        fileId = "file",
                        hash = "0123456789012345678901234567890123456789",
                    ) to stagedClient
                ),
                onProgress = {}
            ).getOrThrow()

            assertTrue(loaded.containsExcludedMcaFiles)
            val extraPaths = loaded.serverExtraFiles.map { it.relativePath }.toSet()
            mediaExtensions.forEach { extension ->
                assertFalse(extraPaths.any { it.substringAfterLast('.').equals(extension, ignoreCase = true) })
            }
            assertTrue(extraPaths.contains("server.json"))
            assertTrue(extraPaths.contains("server.toml"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `embedded source survives local payload conversion`() {
        val source = EmbeddedModSource(
            mod = Mod(
                platform = "mr",
                projectId = "project",
                slug = "example",
                fileId = "file",
                hash = "0123456789012345678901234567890123456789",
            ),
            stagedFile = File("work/embedded-mods/example.jar"),
            originalFileName = "example-original.jar",
        )
        val loaded = LoadedLocalModpack(
            sourceType = LocalModpackSourceType.MODRINTH,
            sourceDir = File("source"),
            packName = "pack",
            packVersion = "1",
            mcVersion = McVersion.V211,
            modloader = ModLoader.neoforge,
            mods = listOf(source.mod),
            embeddedModSources = listOf(source),
        )

        val roundTrip = loaded.toUploadPayload().toLoadedLocalModpack()

        assertEquals(listOf(source), roundTrip.embeddedModSources)
    }

    @Test
    fun `server matching reads the caller provided staged client source`() = runBlocking {
        val root = Files.createTempDirectory("pack-proc-server-match").toFile()
        try {
            val serverMods = root.resolve("mods").also { it.mkdirs() }
            val stagedClient = root.resolve("staged/original-client-name.jar")
                .also { it.parentFile.mkdirs() }
            writeModJar(stagedClient, "shared")
            writeModJar(serverMods.resolve("server-shared.jar"), "shared")

            val clientMod = Mod(
                platform = "mr",
                projectId = "project",
                slug = "shared-mod",
                fileId = "file",
                hash = "0123456789012345678901234567890123456789",
            )
            val loaded = ModpackProcessor(
                PackProcessingPaths(root.resolve("work"))
            ).loadServerPack(
                file = root,
                clientMods = listOf(clientMod),
                clientModSources = mapOf(clientMod to stagedClient),
                onProgress = {},
            ).getOrThrow()

            assertEquals(Mod.Side.BOTH, loaded.mods.single().side)
            assertEquals("shared-mod", loaded.mods.single().slug)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `server duplicate mod keeps newest version and excludes discarded jar`() = runBlocking {
        val scenarios = listOf(
            listOf("cc-tweaked-1.113.1.jar" to "1.113.1", "cc-tweaked-1.120.2.jar" to "1.120.2"),
            listOf("cc-tweaked-1.120.2.jar" to "1.120.2", "cc-tweaked-1.113.1.jar" to "1.113.1")
        )
        scenarios.forEachIndexed { index, serverFiles ->
            val root = Files.createTempDirectory("pack-proc-server-duplicate-$index").toFile()
            try {
                val serverMods = root.resolve("mods").also { it.mkdirs() }
                val stagedClient = root.resolve("staged/client.jar")
                    .also { it.parentFile.mkdirs() }
                writeNeoForgeModJar(stagedClient, "computercraft", "1.0.0")
                serverFiles.forEach { (fileName, version) ->
                    writeNeoForgeModJar(serverMods.resolve(fileName), "computercraft", version)
                }

                val clientMod = Mod(
                    platform = "mr",
                    projectId = "project",
                    slug = "computercraft",
                    fileId = "file",
                    hash = "0123456789012345678901234567890123456789",
                )
                val loaded = ModpackProcessor(
                    PackProcessingPaths(root.resolve("work"))
                ).loadServerPack(
                    file = root,
                    clientMods = listOf(clientMod),
                    clientModSources = mapOf(clientMod to stagedClient),
                    onProgress = {},
                ).getOrThrow()

                assertEquals("cc-tweaked-1.120.2.jar", loaded.embeddedModSources.single().originalFileName)
                assertFalse(loaded.serverExtraFiles.any { it.sourceFile.name == "cc-tweaked-1.113.1.jar" })
            } finally {
                root.deleteRecursively()
            }
        }
    }

    private fun writeModJar(file: File, modId: String, version: String? = null) {
        val versionJson = version?.let { ",\"version\":\"$it\"" }.orEmpty()
        file.outputStream().use { output ->
            JarOutputStream(output).use { jar ->
                jar.putNextEntry(JarEntry("mcmod.info"))
                jar.write("[{\"modid\":\"$modId\"$versionJson}]".toByteArray())
                jar.closeEntry()
            }
        }
    }

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    private fun modrinthIndexBytes(): ByteArray = """
        {
          "formatVersion": 1,
          "game": "minecraft",
          "versionId": "test",
          "name": "test",
          "files": [],
          "dependencies": {"minecraft": "1.21.1", "neoforge": "21.1.0"}
        }
    """.trimIndent().toByteArray()

    private fun assertNestedZipEntryEquals(archiveBytes: ByteArray, path: String, expected: ByteArray) {
        var found = false
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == path) {
                    assertContentEquals(expected, zip.readBytes())
                    found = true
                }
            }
        }
        assertTrue(found, "Missing nested archive entry: $path")
    }

    private fun assertNestedZipEntryMissing(archiveBytes: ByteArray, path: String) {
        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                assertFalse(entry.name == path, "Unexpected nested archive entry: $path")
            }
        }
    }

    private fun writeNeoForgeModJar(file: File, modId: String, version: String) {
        file.outputStream().use { output ->
            JarOutputStream(output).use { jar ->
                jar.putNextEntry(JarEntry("META-INF/neoforge.mods.toml"))
                jar.write(
                    """
                    [[mods]]
                    modId = "$modId"
                    version = "$version"
                    """.trimIndent().toByteArray()
                )
                jar.closeEntry()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun noCallModCatalog(): ModCatalog = Proxy.newProxyInstance(
        ModCatalog::class.java.classLoader,
        arrayOf(ModCatalog::class.java),
    ) { _, method, _ ->
        when {
            method.name.takeWhile { it != '-' } == "getMetadata" ->
                emptyMap<CatalogSlugRef, CatalogModMetadata>()
            else -> error("catalog should not be called: ${method.name}")
        }
    } as ModCatalog
}
