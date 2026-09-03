package calebxzhou.rdi.master.service.host

import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.service.CLIENT_ONLY_MARK_PREFIX
import org.bson.types.ObjectId
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostControlServiceTest {
    @Test
    fun `missing mods directory rejects`() = withFixture { host, version, mods, cache ->
        assertFalse(hasEnabledKotlinForForge(host, version, mods.resolveSibling("missing"), cache))
    }

    @Test
    fun `empty mods directory rejects`() = withFixture { host, version, mods, cache ->
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `physical kotlinforforge jar still accepts`() = withFixture { host, version, mods, cache ->
        mods.resolve("kotlinforforge-5.0.0.jar").writeText("")
        assertTrue(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `mixed case physical kotlin for forge jar accepts`() = withFixture { host, version, mods, cache ->
        mods.resolve("Kotlin-For-Forge-5.0.0.JAR").writeText("")
        assertTrue(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `unrelated physical jar rejects`() = withFixture { host, version, mods, cache ->
        mods.resolve("jei-1.0.0.jar").writeText("")
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `disabled physical jar rejects`() = withFixture { host, version, mods, cache ->
        mods.resolve("kotlinforforge-5.0.0.jar.disabled").writeText("")
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `non jar physical kotlin for forge file rejects`() = withFixture { host, version, mods, cache ->
        mods.resolve("kotlinforforge-5.0.0.zip").writeText("")
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `matching physical directory rejects`() = withFixture { host, version, mods, cache ->
        mods.resolve("kotlinforforge-5.0.0.jar").mkdirs()
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `nested physical matching jar rejects`() = withFixture { host, version, mods, cache ->
        mods.resolve("nested").mkdirs()
        mods.resolve("nested/kotlinforforge-5.0.0.jar").writeText("")
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    @Test
    fun `base canonical candidate accepts`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlin-for-forge")
        cacheFile(cache, mod.fileName)
        assertTrue(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `base legacy candidate accepts`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        cacheFile(cache, mod.legacyFileName)
        assertTrue(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `disabled base candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        cacheFile(cache, mod.fileName)
        val disabledHost = host.copy(disabledMods = listOf(mod))
        assertFalse(hasEnabledKotlinForForge(disabledHost, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `base client candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge", side = Mod.Side.CLIENT)
        cacheFile(cache, mod.fileName)
        assertFalse(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `base unknown candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge", side = Mod.Side.UNKNOWN)
        cacheFile(cache, mod.fileName)
        assertFalse(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `base missing candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        assertFalse(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `extra server candidate accepts`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlin-for-forge", side = Mod.Side.SERVER)
        cacheFile(cache, mod.fileName)
        val hostWithExtra = host.copy(extraMods = listOf(mod))
        assertTrue(hasEnabledKotlinForForge(hostWithExtra, version, mods, cache))
    }

    @Test
    fun `extra both candidate accepts`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        cacheFile(cache, mod.fileName)
        val hostWithExtra = host.copy(extraMods = listOf(mod))
        assertTrue(hasEnabledKotlinForForge(hostWithExtra, version, mods, cache))
    }

    @Test
    fun `extra client and unknown candidates reject`() = withFixture { host, version, mods, cache ->
        val client = mod("kotlinforforge", side = Mod.Side.CLIENT)
        val unknown = mod("kotlin-for-forge", side = Mod.Side.UNKNOWN)
        cacheFile(cache, client.fileName)
        cacheFile(cache, unknown.fileName)
        val hostWithExtra = host.copy(extraMods = listOf(client, unknown))
        assertFalse(hasEnabledKotlinForForge(hostWithExtra, version, mods, cache))
    }

    @Test
    fun `extra missing candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        val hostWithExtra = host.copy(extraMods = listOf(mod))
        assertFalse(hasEnabledKotlinForForge(hostWithExtra, version, mods, cache))
    }

    @Test
    fun `candidate directory rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("kotlinforforge")
        cache.resolve(mod.fileName).mkdirs()
        assertFalse(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `client only marked base candidate rejects`() = withFixture { host, version, mods, cache ->
        val mod = mod("${CLIENT_ONLY_MARK_PREFIX}kotlinforforge")
        cacheFile(cache, mod.fileName)
        assertFalse(hasEnabledKotlinForForge(host, version.copy(mods = mutableListOf(mod)), mods, cache))
    }

    @Test
    fun `physical non jar and nested entries reject`() = withFixture { host, version, mods, cache ->
        mods.resolve("kotlinforforge-5.0.0.zip").writeText("")
        mods.resolve("nested").mkdirs()
        mods.resolve("nested/kotlinforforge-5.0.0.jar").writeText("")
        assertFalse(hasEnabledKotlinForForge(host, version, mods, cache))
    }

    private fun mod(slug: String, side: Mod.Side = Mod.Side.BOTH) = Mod(
        platform = "cf",
        projectId = slug,
        slug = slug,
        fileId = "file-$slug",
        hash = "hash-$slug",
        side = side,
    )

    private fun cacheFile(cache: File, fileName: String) {
        cache.resolve(fileName).writeText("")
    }

    private fun withFixture(block: (Host, Modpack.Version, File, File) -> Unit) {
        val root = Files.createTempDirectory("host-start-kotlin").toFile()
        try {
            val hostMods = root.resolve("host-mods").apply { mkdirs() }
            val cache = root.resolve("mod-cache").apply { mkdirs() }
            val host = Host(
                name = "test-host",
                ownerId = ObjectId(),
                modpackId = ObjectId(),
                port = 25565,
                difficulty = 2,
                gameMode = 0,
                levelType = "default",
            )
            val version = Modpack.Version(
                time = 0L,
                modpackId = host.modpackId,
                name = "1.0.0",
                changelog = "",
                status = Modpack.Status.OK,
            )
            block(host, version, hostMods, cache)
        } finally {
            root.deleteRecursively()
        }
    }
}
