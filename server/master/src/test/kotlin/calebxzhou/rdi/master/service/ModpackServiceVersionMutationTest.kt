package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Host
import calebxzhou.rdi.common.model.ModRef
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.master.service.ModpackService.addVersionMod
import calebxzhou.rdi.master.service.ModpackService.addVersionMods
import calebxzhou.rdi.master.service.ModpackService.changeOptions
import calebxzhou.rdi.master.service.ModpackService.deleteVersion
import calebxzhou.rdi.master.service.ModpackService.removeVersionMod
import calebxzhou.rdi.master.service.ModpackService.removeVersionMods
import calebxzhou.rdi.master.service.ModpackService.replaceVersionMod
import calebxzhou.rdi.master.service.host.HostQueryService
import calebxzhou.rdi.master.service.modpack.ModpackBuildService
import calebxzhou.rdi.master.service.modpack.ModpackVersionService.versionMutationExpectation
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.FindFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.bson.conversions.Bson
import com.mongodb.client.model.UpdateOptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.FlowCollector
import com.mongodb.client.result.UpdateResult

class ModpackServiceVersionMutationTest {
    private lateinit var collection: MongoCollection<Modpack>
    private val submitted = mutableListOf<Pair<calebxzhou.rdi.common.model.Task2, String?>>()
    private val observedVersions = mutableListOf<Modpack.Version>()
    private val filters = mutableListOf<Bson>()
    private val updates = mutableListOf<Bson>()
    private val prepared = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        mockkObject(HostQueryService)
        coEvery { HostQueryService.findByModpackVersion(any(), any()) } returns emptyList()
        ModpackService.testServerModPreparer = { prepared += it.slug }
        val updateResult = mockk<UpdateResult>()
        every { updateResult.matchedCount } returns 1L
        every { updateResult.modifiedCount } returns 1L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) } coAnswers {
            filters += firstArg<Bson>()
            updates += secondArg<Bson>()
            updateResult
        }
        ServerTaskManager.testSubmitter = { task, key, _ ->
            submitted += task to key
            "test-run"
        }
        ModpackService.testBuildVersionObserver = { observedVersions += it }
    }

    @AfterTest
    fun cleanup() {
        ServerTaskManager.testSubmitter = null
        ModpackService.testBuildVersionObserver = null
        ModpackService.testServerModDownloadTaskFactory = null
        ModpackService.testServerModPreparer = null
        ModpackService.testClientModDownloadTaskFactory = null
        ModpackService.testDbcl = null
        unmockkObject(HostQueryService)
    }

    private fun useFreshPack(pack: Modpack) {
        val flow = mockk<FindFlow<Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        coEvery { flow.collect(any()) } coAnswers {
            firstArg<FlowCollector<Modpack>>().emit(pack)
        }
    }

    @Test
    fun `adding mods normalizes sorts and publishes without a build`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, mods = mutableListOf(ModpackServiceTestFixtures.mod("zeta")))
        val ctx = ModpackContext(player, pack, version)
        pack.versions += version
        useFreshPack(pack)

        ctx.addVersionMods(
            listOf(
                ModpackServiceTestFixtures.mod("spark"),
                ModpackServiceTestFixtures.mod("status-effect-bars-reforged"),
                ModpackServiceTestFixtures.mod("server-addon")
            )
        )

        assertEquals(1, updates.size)
        assertTrue(filters.single().toString().isNotBlank())
        val expected = version.versionMutationExpectation()
        assertEquals(version.name, expected.versionName)
        assertEquals(Modpack.Status.OK, expected.status)
        assertEquals(version.mods.toList(), expected.oldMods)
        val update = updates.single().toString()
        assertTrue(update.contains("status-effect-bars-reforged"))
        assertTrue(update.contains("zeta"))
        assertTrue(update.contains("server-addon"))
        assertEquals(listOf("server-addon"), prepared)
        assertTrue(!updates.single().toString().contains("WAIT"))
        assertTrue(submitted.isEmpty())
        coVerify(exactly = 1) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
    }

    @Test
    fun `mutation rejects empty duplicate blank and missing targets without persistence`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val existing = ModpackServiceTestFixtures.mod("existing")
        val version = ModpackServiceTestFixtures.version(pack, mods = mutableListOf(existing))
        val ctx = ModpackContext(player, pack, version)
        pack.versions += version
        useFreshPack(pack)

        assertFailsWith<Exception> { ctx.addVersionMods(emptyList()) }
        assertFailsWith<Exception> { ctx.addVersionMods(listOf(existing, existing)) }
        assertFailsWith<Exception> { ctx.addVersionMods(listOf(ModpackServiceTestFixtures.mod("existing"))) }
        assertFailsWith<Exception> { ctx.removeVersionMods(listOf(ModRef(" ", "x"))) }
        assertFailsWith<Exception> { ctx.removeVersionMod("missing", "missing") }

        coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `replace and remove match trimmed project and file ids`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val current = ModpackServiceTestFixtures.mod("old", projectId = "project", fileId = "file")
        val version = ModpackServiceTestFixtures.version(pack, mods = mutableListOf(current))
        val ctx = ModpackContext(player, pack, version)
        pack.versions += version
        useFreshPack(pack)

        ctx.replaceVersionMod(" project ", " file ", ModpackServiceTestFixtures.mod("new", projectId = "new", fileId = "new-file"))
        ctx.removeVersionMod(" project ", " file ")
        assertEquals("old", version.mods.single().slug)
        assertEquals(2, updates.size)
        assertTrue(updates[0].toString().contains("new"))
        assertTrue(updates[1].toString().contains("mods"))
        assertEquals(1, prepared.size)
        assertTrue(submitted.isEmpty())
        coVerify(exactly = 2) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
    }

    @Test
    fun `replace with only a URL change publishes without preparing the artifact`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val current = ModpackServiceTestFixtures.mod("same", projectId = "project", fileId = "file")
        val version = ModpackServiceTestFixtures.version(pack, mods = mutableListOf(current))
        pack.versions += version
        useFreshPack(pack)

        val replacement = current.copy(downloadUrls = listOf("https://example.invalid/new.jar"))
        ModpackContext(player, pack, version).replaceVersionMod("project", "file", replacement)

        assertTrue(prepared.isEmpty())
        assertEquals(1, updates.size)
        assertTrue(updates.single().toString().contains("new.jar"))
    }

    @Test
    fun `client to both prepares server artifact while both to client does not`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val client = ModpackServiceTestFixtures.mod("transition", side = Mod.Side.CLIENT)
        val first = ModpackServiceTestFixtures.version(pack, mods = mutableListOf(client))
        pack.versions += first
        useFreshPack(pack)
        ModpackContext(player, pack, first).replaceVersionMod(
            client.projectId,
            client.fileId,
            client.copy(side = Mod.Side.BOTH)
        )
        assertEquals(listOf("transition"), prepared)

        prepared.clear()
        updates.clear()
        val both = ModpackServiceTestFixtures.mod("transition", side = Mod.Side.BOTH)
        val second = ModpackServiceTestFixtures.version(pack, name = "1.1.0", mods = mutableListOf(both))
        pack.versions += second
        useFreshPack(pack)
        ModpackService.testClientModDownloadTaskFactory = { Task2.Leaf("client-cache") { } }
        ModpackContext(player, pack, second).replaceVersionMod(
            both.projectId,
            both.fileId,
            both.copy(side = Mod.Side.CLIENT)
        )
        assertTrue(prepared.isEmpty())
    }

    @Test
    fun `mandatory preparation failure leaves database untouched and names the mod`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack)
        pack.versions += version
        useFreshPack(pack)
        ModpackService.testServerModPreparer = { mod ->
            throw IllegalStateException("network unavailable for ${mod.slug}")
        }

        val error = assertFailsWith<RequestError> {
            ModpackContext(player, pack, version).addVersionMod(ModpackServiceTestFixtures.mod("broken"))
        }
        assertTrue(error.message!!.contains("broken"))
        assertTrue(error.message!!.contains("network unavailable"))
        assertTrue(updates.isEmpty())
        assertTrue(submitted.isEmpty())
        assertEquals(Modpack.Status.OK, version.status)
    }

    @Test
    fun `no-op processor result does not prepare or publish`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack)
        pack.versions += version
        useFreshPack(pack)

        ModpackContext(player, pack, version).addVersionMod(ModpackServiceTestFixtures.mod("spark"))

        assertTrue(prepared.isEmpty())
        assertTrue(updates.isEmpty())
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `CAS conflict rejects publication without stale overwrite`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack)
        pack.versions += version
        useFreshPack(pack)
        val conflict = mockk<UpdateResult>()
        every { conflict.matchedCount } returns 0L
        every { conflict.modifiedCount } returns 0L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) } returns conflict

        val error = assertFailsWith<RequestError> {
            ModpackContext(player, pack, version).addVersionMod(ModpackServiceTestFixtures.mod("new"))
        }
        assertTrue(error.message!!.contains("刷新"))
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `client cache failure is nonfatal`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack)
        pack.versions += version
        useFreshPack(pack)
        ModpackService.testClientModDownloadTaskFactory = {
            Task2.Leaf("client-cache-fails") { error("cache unavailable") }
        }

        ModpackContext(player, pack, version).addVersionMod(
            ModpackServiceTestFixtures.mod("client", side = Mod.Side.CLIENT)
        )

        assertEquals(1, updates.size)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `deleting a busy version does not remove files or pull the database`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        listOf(Modpack.Status.WAIT, Modpack.Status.BUILDING).forEach { status ->
            val version = ModpackServiceTestFixtures.version(pack, name = "delete-${status.name}", status = status)
            pack.versions += version
            useFreshPack(pack)
            assertFailsWith<RequestError> { ModpackContext(player, pack, version).deleteVersion() }
        }
        coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
    }

    @Test
    fun `deleting a version is blocked only by a matching active build task`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, name = "target")
        pack.versions += version
        useFreshPack(pack)
        ServerTaskManager.testSubmitter = null
        val otherRunId = ServerTaskManager.submit(
            Task2.Leaf("other queued") { },
            ModpackBuildService.versionBuildTaskKey(pack._id, "other"),
            autoStart = false
        )
        try {
            ModpackContext(player, pack, version).deleteVersion()
            coVerify(exactly = 1) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
        } finally {
            ServerTaskManager.remove(otherRunId)
        }

        val matchingRunId = ServerTaskManager.submit(
            Task2.Leaf("matching queued") { },
            ModpackBuildService.versionBuildTaskKey(pack._id, version.name),
            autoStart = false
        )
        try {
            useFreshPack(pack)
            assertFailsWith<RequestError> { ModpackContext(player, pack, version).deleteVersion() }
        } finally {
            ServerTaskManager.remove(matchingRunId)
        }
    }

    @Test
    fun `deletion removes the database version before cleaning archives`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val oldMod = ModpackServiceTestFixtures.mod("ordered")
        val version = ModpackServiceTestFixtures.version(
            pack,
            name = "ordered-delete",
            mods = mutableListOf(oldMod)
        )
        pack.versions += version
        version.storageDir.mkdirs()
        version.zip.writeBytes(byteArrayOf(1))
        useFreshPack(pack)
        try {
            ModpackContext(player, pack, version).deleteVersion()
            assertTrue(filters.single().toString().isNotBlank())
            val expected = version.versionMutationExpectation()
            assertEquals(version.name, expected.versionName)
            assertEquals(Modpack.Status.OK, expected.status)
            assertEquals(version.mods.toList(), expected.oldMods)
            assertTrue(updates.single().toString().contains("versions"))
            assertTrue(!version.zip.exists())
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `failed conditional deletion leaves archives untouched`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, name = "failed-delete")
        pack.versions += version
        version.storageDir.mkdirs()
        version.zip.writeBytes(byteArrayOf(1))
        useFreshPack(pack)
        val conflict = mockk<UpdateResult>()
        every { conflict.matchedCount } returns 0L
        every { conflict.modifiedCount } returns 0L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) } returns conflict
        try {
            val error = assertFailsWith<RequestError> {
                ModpackContext(player, pack, version).deleteVersion()
            }
            assertTrue(error.message!!.contains("刷新"))
            assertTrue(version.zip.exists())
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `persisted stopped room reference blocks deletion`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, name = "pending-room")
        pack.versions += version
        val room = mockk<Host>(relaxed = true)
        every { room.name } returns "pending-room"
        coEvery { HostQueryService.findByModpackVersion(pack._id, version.name) } returns listOf(room)
        useFreshPack(pack)

        val error = assertFailsWith<RequestError> {
            ModpackContext(player, pack, version).deleteVersion()
        }
        assertTrue(error.message!!.contains("pending-room"))
        coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
    }

    @Test
    fun `wait and building versions cannot be edited`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        listOf(Modpack.Status.WAIT, Modpack.Status.BUILDING).forEach { status ->
            val version = ModpackServiceTestFixtures.version(pack, name = "${status.name.lowercase()}-version", status = status)
            val ctx = ModpackContext(player, pack, version)
            pack.versions += version
            useFreshPack(pack)
            assertFailsWith<Exception> { ctx.addVersionMod(ModpackServiceTestFixtures.mod("new-$status")) }
        }
        val failVersion = ModpackServiceTestFixtures.version(pack, name = "failed-version", status = Modpack.Status.FAIL)
        pack.versions += failVersion
        useFreshPack(pack)
        assertFailsWith<Exception> {
            ModpackContext(player, pack, failVersion).addVersionMod(ModpackServiceTestFixtures.mod("new-fail"))
        }
        coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun `change options normalizes categories and persists requested fields`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val ctx = ModpackContext(player, pack, null)
        ctx.changeOptions(
            Modpack.OptionsDto(
                name = " New Name ",
                iconUrl = null,
                info = "updated description",
                sourceUrl = " https://example.invalid/source ",
                categories = listOf(Modpack.Category.TECH, Modpack.Category.TECH)
            )
        )
        assertEquals(1, updates.size)
        val update = updates.single().toString()
        assertTrue(update.contains("New Name"))
        assertTrue(update.contains("updated description"))
        assertTrue(update.contains("TECH"))
        assertTrue(update.contains("source"))
    }
}
