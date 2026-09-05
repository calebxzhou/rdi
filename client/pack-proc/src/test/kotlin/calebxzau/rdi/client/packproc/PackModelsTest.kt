package calebxzau.rdi.client.packproc

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.ModLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PackModelsTest {
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
}
