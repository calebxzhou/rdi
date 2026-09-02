package calebxzhou.rdi.master.service.host2

import calebxzau.rdi.common.model.ContentOrigin
import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.ContentVo
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Host2PackStatus
import calebxzhou.rdi.common.model.PackSource
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Host2ContentRulesTest {
    @Test
    fun `extra content shadows pack content by identity and target path`() {
        val identityPack = content(ContentOrigin.Pack, "identity", "mods/identity-pack.jar")
        val identityExtra = content(ContentOrigin.Extra, "identity", "mods/identity-extra.jar")
        val pathPack = content(ContentOrigin.Pack, "path-pack", "mods/shared.jar")
        val pathExtra = content(ContentOrigin.Extra, "path-extra", "mods/shared.jar")

        val effective = effectiveHost2Contents(listOf(identityPack, identityExtra, pathPack, pathExtra))

        assertEquals(listOf(identityExtra, pathExtra), effective)
        assertEquals(
            listOf(identityPack, pathPack),
            shadowedHost2PackContents(listOf(identityPack, identityExtra, pathPack, pathExtra)),
        )
        assertEquals(listOf(identityPack, pathPack), effectiveHost2Contents(listOf(identityPack, pathPack)))
    }

    @Test
    fun `content targets protect platform files and constrain world writes`() {
        listOf(
            "server.properties",
            "eula.txt",
            "libraries/example.jar",
            "logs/latest.log",
            "world/level.dat",
            "server.jar",
            "config/plugin.jar",
        ).forEach { target ->
            assertFailsWith<RequestError>(target) {
                content(ContentOrigin.Extra, target, target).validatedTarget()
            }
        }

        assertEquals(
            "world/datapacks/example/data.json",
            content(ContentOrigin.Extra, "datapack", "world/datapacks/example/data.json")
                .validatedTarget().targetPath,
        )
    }

    @Test
    fun `same layer target collision is rejected before extra shadowing`() {
        val first = content(ContentOrigin.Pack, "first", "mods/shared.jar")
        val second = content(ContentOrigin.Pack, "second", "mods/shared.jar")
        val extra = content(ContentOrigin.Extra, "extra", "mods/shared.jar")

        assertFailsWith<RequestError> { validateHost2LayerPathConflicts(listOf(first, second, extra)) }
    }

    @Test
    fun `same project client and server artifacts can coexist in one snapshot`() {
        val client = content(ContentOrigin.Pack, "dual", "mods/dual-client.jar")
            .copy(side = ContentSide.Client)
        val server = content(ContentOrigin.Pack, "dual", "mods/dual-server.jar")
            .copy(side = ContentSide.Server)

        validateHost2SnapshotIdentity(listOf(client, server))
    }

    @Test
    fun `same project duplicate on one side is still rejected`() {
        val first = content(ContentOrigin.Pack, "duplicate", "mods/first.jar")
            .copy(side = ContentSide.Client)
        val duplicate = first.copy(fileId = "different-file", targetPath = "mods/second.jar")

        assertFailsWith<RequestError> { validateHost2SnapshotIdentity(listOf(first, duplicate)) }
    }

    @Test
    fun `server pack filter removes runtime owned files but keeps mod jars`() {
        val root = Files.createTempDirectory("host2-server-filter").toFile()
        try {
            root.resolve("libraries/library.jar").write("library")
            root.resolve("logs/latest.log").write("log")
            root.resolve("world/session.lock").write("lock")
            root.resolve("config/cache.db").write("db")
            root.resolve("preview.png").write("image")
            root.resolve("server.jar").write("server")
            root.resolve("mods/example.jar").write("mod")
            root.resolve("lwjgl3ify-forgePatches.jar").write("launcher")

            filterHost2ServerPack(root)

            assertFalse(root.resolve("libraries").exists())
            assertFalse(root.resolve("logs").exists())
            assertFalse(root.resolve("world/session.lock").exists())
            assertFalse(root.resolve("config/cache.db").exists())
            assertFalse(root.resolve("preview.png").exists())
            assertFalse(root.resolve("server.jar").exists())
            assertTrue(root.resolve("mods/example.jar").isFile)
            assertTrue(root.resolve("lwjgl3ify-forgePatches.jar").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `kotlin for forge validation ignores disabled file and rejects two enabled files`() {
        val root = Files.createTempDirectory("host2-kff").toFile()
        try {
            root.resolve("mods/KotlinForForge-one.jar.disabled").write("disabled")
            validateKotlinForForge(root)
            root.resolve("mods/kotlinforforge-one.jar").write("one")
            validateKotlinForForge(root)
            root.resolve("mods/KOTLIN-FOR-FORGE-two.jar").write("two")
            assertFailsWith<RequestError> { validateKotlinForForge(root) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `deployment marker must match host source and active revision`() {
        val root = Files.createTempDirectory("host2-marker").toFile()
        val hostId = UUID.randomUUID()
        val source = PackSource.Modpack2(UUID.randomUUID())
        val host = Host2Record(
            id = hostId,
            name = "test",
            intro = "test",
            iconUrl = null,
            ownerId = UUID.randomUUID(),
            packSource = source,
            packStatus = Host2PackStatus.Ok,
            activeContentRevision = 3,
            pendingContentRevision = null,
            port = 30000,
            whitelist = false,
        )
        try {
            writeDeploymentMarker(root, hostId, UUID.randomUUID(), 3, source)

            validateDeploymentMarker(root, host)
            assertFailsWith<RequestError> {
                validateDeploymentMarker(root, host.copy(activeContentRevision = 4))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun content(origin: ContentOrigin, projectId: String, targetPath: String) = ContentVo(
        origin = origin,
        platform = ContentPlatform.Modrinth,
        type = ContentType.Mod,
        projectId = projectId,
        fileId = "file-$projectId",
        slug = projectId,
        hash = "a".repeat(40),
        targetPath = targetPath,
        side = ContentSide.Both,
        fileSize = 1,
    )

    private fun java.io.File.write(value: String) {
        parentFile.mkdirs()
        writeText(value)
    }
}
