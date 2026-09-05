package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.McVersion
import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.master.service.ModpackService.createVersion
import calebxzhou.rdi.master.service.ModpackService.createWithVersion
import calebxzhou.rdi.common.service.validateIconUrl
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.client.model.InsertOneOptions
import com.mongodb.client.model.DeleteOptions
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.bson.conversions.Bson
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModpackServiceUploadTest {
    private lateinit var collection: MongoCollection<Modpack>

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        mockkStatic("calebxzhou.rdi.common.service.ModpackXtKt")
        coEvery { validateIconUrl(any()) } returns Result.success(Unit)
        coEvery { collection.countDocuments(any<Bson>()) } returns 0L
    }

    @AfterTest
    fun cleanup() {
        ServerTaskManager.testSubmitter = null
        unmockkStatic("calebxzhou.rdi.common.service.ModpackXtKt")
        ModpackService.testDbcl = null
    }

    @Test
    fun `create version accepts tar zst moves archive and queues build`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val root = ModpackServiceTestFixtures.tempRoot()
        val upload = ModpackServiceTestFixtures.writeTarZst(root, "overrides/config/test.txt" to "ok".encodeToByteArray())
        val update = mockk<UpdateResult>()
        every { update.modifiedCount } returns 1L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } returns update
        var submitted = false
        ServerTaskManager.testSubmitter = { _, _, _ -> submitted = true; "test" }
        try {
            ModpackContext(player, pack, null).createVersion(
                "1.0.0",
                upload,
                mutableListOf(ModpackServiceTestFixtures.mod("example"))
            )
            assertTrue(pack.dir.resolve("1.0.0.tar.zst").exists())
            assertFalse(upload.exists())
            assertTrue(submitted)
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `create version rejects zip and unsupported minecraft versions`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id, mcVersion = McVersion.V122)
        val root = ModpackServiceTestFixtures.tempRoot()
        val zip = root.resolve("pack.zip").apply { writeBytes(byteArrayOf(80, 75, 3, 4)) }
        try {
            assertFailsWith<Exception> {
                ModpackContext(player, pack, null).createVersion("1.0.0", zip, mutableListOf())
            }
            assertFalse(pack.dir.exists())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `create pack requires microsoft account and valid metadata`() = runTest {
        val player = ModpackServiceTestFixtures.account().copy(msid = null)
        val dto = Modpack.CreateWithVersionDto(
            name = "New Pack",
            mcVer = McVersion.V211,
            modLoader = McVersion.V211.loaderVersions.keys.first(),
            verName = "1.0.0",
            info = "valid description with enough characters",
            iconUrl = "https://example.invalid/icon.png",
            categories = listOf(Modpack.Category.OTHER),
            mods = mutableListOf()
        )
        val root = ModpackServiceTestFixtures.tempRoot()
        val archive = ModpackServiceTestFixtures.writeTarZst(root, "overrides/config/test.txt" to byteArrayOf(1))
        try {
            assertFailsWith<Exception> { dto.createWithVersion(player, archive) }
            assertTrue(archive.exists())
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `create pack persists processed version and moves tar archive`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val dto = Modpack.CreateWithVersionDto(
            name = "New Pack",
            mcVer = McVersion.V211,
            modLoader = McVersion.V211.loaderVersions.keys.first(),
            verName = "1.0.0",
            info = "valid description with enough characters",
            iconUrl = "https://modrinth.com/icon.png",
            categories = listOf(Modpack.Category.OTHER),
            mods = mutableListOf(
                ModpackServiceTestFixtures.mod("status-effect-bars-reforged", side = Mod.Side.BOTH),
                ModpackServiceTestFixtures.mod("spark")
            )
        )
        val root = ModpackServiceTestFixtures.tempRoot()
        val archive = ModpackServiceTestFixtures.writeTarZst(root, "overrides/config/test.txt" to byteArrayOf(1))
        val inserted = slot<Modpack>()
        coEvery { collection.insertOne(capture(inserted), any()) } returns mockk(relaxed = true)
        ServerTaskManager.testSubmitter = { _, _, _ -> "test" }
        try {
            dto.createWithVersion(player, archive)
            val stored = inserted.captured
            assertEquals(1, stored.versions.size)
            assertEquals(listOf("status-effect-bars-reforged"), stored.versions.single().mods.map { it.slug })
            assertEquals(Mod.Side.CLIENT, stored.versions.single().mods.single().side)
            assertTrue(stored.dir.resolve("1.0.0.tar.zst").exists())
        } finally {
            if (inserted.isCaptured) inserted.captured.dir.deleteRecursivelyNoSymlink()
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `create pack insertion failure rolls back archive and document`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val dto = Modpack.CreateWithVersionDto(
            name = "Rollback Pack",
            mcVer = McVersion.V211,
            modLoader = McVersion.V211.loaderVersions.keys.first(),
            verName = "1.0.0",
            info = "valid description with enough characters",
            iconUrl = "https://modrinth.com/icon.png",
            categories = listOf(Modpack.Category.OTHER),
            mods = mutableListOf()
        )
        val root = ModpackServiceTestFixtures.tempRoot()
        val archive = ModpackServiceTestFixtures.writeTarZst(root, "overrides/config/test.txt" to byteArrayOf(1))
        coEvery { collection.insertOne(any<Modpack>(), any<InsertOneOptions>()) } throws IllegalStateException("insert failed")
        try {
            assertFailsWith<IllegalStateException> { dto.createWithVersion(player, archive) }
            assertTrue(!archive.exists())
            coVerify(exactly = 1) { collection.deleteOne(any<Bson>(), any<DeleteOptions>()) }
        } finally {
            root.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `create version duplicate modified count rolls back moved archive`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val root = ModpackServiceTestFixtures.tempRoot()
        val archive = ModpackServiceTestFixtures.writeTarZst(root, "overrides/config/test.txt" to byteArrayOf(1))
        val update = mockk<UpdateResult>()
        every { update.modifiedCount } returns 0L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } returns update
        try {
            assertFailsWith<Exception> {
                ModpackContext(player, pack, null).createVersion("1.0.0", archive, mutableListOf())
            }
            assertTrue(!pack.dir.resolve("1.0.0.tar.zst").exists())
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
            root.deleteRecursivelyNoSymlink()
        }
    }
}
