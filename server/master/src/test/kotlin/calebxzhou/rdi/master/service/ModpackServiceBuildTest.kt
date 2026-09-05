package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.service.runInline
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.util.deleteRecursivelyNoSymlink
import calebxzhou.rdi.common.model.Mail
import calebxzhou.rdi.master.service.ModpackService.rebuildVersion
import calebxzhou.rdi.master.service.ModpackService.buildVersion
import calebxzhou.rdi.master.service.modpack.ModpackBuildService
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.FindFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.FlowCollector
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bson.conversions.Bson
import com.mongodb.client.result.UpdateResult

class ModpackServiceBuildTest {
    private lateinit var collection: MongoCollection<Modpack>

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        val updateResult = mockk<UpdateResult>()
        every { updateResult.matchedCount } returns 1L
        every { updateResult.modifiedCount } returns 1L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } returns updateResult
        mockkObject(MailService)
        coEvery { MailService.sendSystemMail(any(), any(), any()) } returns mockk<Mail>(relaxed = true)
        every { MailService.changeMail(any(), any(), any(), any()) } returns mockk(relaxed = true)
    }

    @AfterTest
    fun cleanup() {
        ServerTaskManager.testSubmitter = null
        ModpackService.testDbcl = null
        ModpackService.testServerModDownloadTaskFactory = null
        ModpackService.testServerModPreparer = null
        ModpackService.testClientModDownloadTaskFactory = null
        unmockkObject(MailService)
    }

    @Test
    fun `rebuild task has stable title and dedupe submission key`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id, name = "Sky Pack")
        val version = ModpackServiceTestFixtures.version(pack, "1.2.3")
        pack.versions += version
        useFreshPack(pack)
        var submitted: String? = null
        ServerTaskManager.testSubmitter = { task, key, autoStart ->
            submitted = key
            assertTrue(autoStart)
            assertEquals("重构整合包版本 Sky Pack V1.2.3", task.title)
            "captured"
        }
        try {
            ModpackContext(player, pack, version).rebuildVersion()
            assertEquals("server-modpack-build:${pack._id.toHexString()}:1.2.3", submitted)
        } finally {
            ServerTaskManager.testSubmitter = null
        }
    }

    private fun useFreshPack(pack: Modpack) {
        val flow = mockk<FindFlow<Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        coEvery { flow.collect(any()) } coAnswers {
            firstArg<FlowCollector<Modpack>>().emit(pack)
        }
    }

    @Test
    fun `manual rebuild persists WAIT before submission and restores status when submission fails`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, status = Modpack.Status.OK)
        pack.versions += version
        useFreshPack(pack)
        val updates = mutableListOf<Bson>()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.matchedCount } returns 1L
        every { updateResult.modifiedCount } returns 1L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            updates += secondArg<Bson>()
            updateResult
        }
        ServerTaskManager.testSubmitter = { _, _, _ ->
            assertTrue(updates.first().toString().contains("WAIT"))
            error("queue unavailable")
        }

        assertFailsWith<IllegalStateException> {
            ModpackContext(player, pack, version).rebuildVersion()
        }
        assertEquals(2, updates.size)
        assertTrue(updates[0].toString().contains("WAIT"))
        assertTrue(updates[1].toString().contains("OK"))
    }

    @Test
    fun `manual rebuild rejects a busy version`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, status = Modpack.Status.BUILDING)
        pack.versions += version
        useFreshPack(pack)

        assertFailsWith<Exception> {
            ModpackContext(player, pack, version).rebuildVersion()
        }
        coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any()) }
    }

    @Test
    fun `active build task detection is exact by version key`() {
        val packId = ModpackServiceTestFixtures.modpack()._id
        val matchingKey = ModpackBuildService.versionBuildTaskKey(packId, "1.0.0")
        val otherKey = ModpackBuildService.versionBuildTaskKey(packId, "2.0.0")
        val matchingRunId = ServerTaskManager.submit(Task2.Leaf("queued") { }, matchingKey, autoStart = false)
        val otherRunId = ServerTaskManager.submit(Task2.Leaf("other") { }, otherKey, autoStart = false)
        try {
            assertTrue(ModpackBuildService.hasActiveVersionBuildTask(packId, "1.0.0"))
            assertTrue(!ModpackBuildService.hasActiveVersionBuildTask(packId, "3.0.0"))
        } finally {
            ServerTaskManager.remove(matchingRunId)
            ServerTaskManager.remove(otherRunId)
        }
    }

    @Test
    fun `manual rebuild rejects a matching active build task`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack)
        pack.versions += version
        useFreshPack(pack)
        val runId = ServerTaskManager.submit(
            Task2.Leaf("already queued") { },
            ModpackBuildService.versionBuildTaskKey(pack._id, version.name),
            autoStart = false
        )
        try {
            assertFailsWith<Exception> {
                ModpackContext(player, pack, version).rebuildVersion()
            }
            coVerify(exactly = 0) { collection.updateOne(any<Bson>(), any<Bson>(), any()) }
        } finally {
            ServerTaskManager.remove(runId)
        }
    }

    @Test
    fun `rebuild partitions use processed client side metadata`() {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(
            pack,
            mods = mutableListOf(
                ModpackServiceTestFixtures.mod("status-effect-bars-reforged", side = Mod.Side.BOTH),
                ModpackServiceTestFixtures.mod("server-addon", side = Mod.Side.BOTH)
            )
        )
        val task = ModpackService.createVersionBuildTaskForTest(player, pack, version, reprocessMods = true)
        val root = task as Task2.Sequence
        val downloadGroup = root.children.filterIsInstance<Task2.Group>().first { it.title == "下载Mod" }
        val serverSequence = downloadGroup.children.filterIsInstance<Task2.Sequence>().first()
        assertEquals("下载1个Mod", serverSequence.children[1].title)
        assertEquals(1, (downloadGroup.children[1] as Task2.Group).children.size)
    }

    @Test
    fun `rebuild executes metadata reprocessing and partitions exact effective mods`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(
            pack,
            mods = mutableListOf(
                ModpackServiceTestFixtures.mod("status-effect-bars-reforged", side = Mod.Side.BOTH),
                ModpackServiceTestFixtures.mod("spark", side = Mod.Side.BOTH),
                ModpackServiceTestFixtures.mod("server-addon", side = Mod.Side.BOTH)
            )
        )
        var serverMods: List<Mod>? = null
        var clientMods: List<Mod>? = null
        ModpackService.testServerModDownloadTaskFactory = { mods ->
            serverMods = mods
            Task2.Leaf("captured-server") { }
        }
        ModpackService.testClientModDownloadTaskFactory = { mods ->
            clientMods = mods
            Task2.Leaf("captured-client") { }
        }
        try {
            val task = ModpackService.createVersionBuildTaskForTest(player, pack, version, reprocessMods = true)
            val root = task as Task2.Sequence
            root.children[1].runInline(Task2Context(emitProgress = {}))
            val downloadGroup = root.children.filterIsInstance<Task2.Group>().first { it.title == "下载Mod" }
            assertEquals(listOf("server-addon"), serverMods!!.map { it.slug })
            assertEquals(listOf("status-effect-bars-reforged"), clientMods!!.map { it.slug })
            assertEquals(listOf("server-addon", "status-effect-bars-reforged"), version.mods.map { it.slug }.sorted())
            assertEquals(Mod.Side.CLIENT, version.mods.first { it.slug == "status-effect-bars-reforged" }.side)
            assertEquals(2, downloadGroup.children.size)
        } finally {
            ModpackService.testServerModDownloadTaskFactory = null
            ModpackService.testClientModDownloadTaskFactory = null
        }
    }

    @Test
    fun `startup recovery marks only unfinished versions failed`() = runTest {
        val pack = ModpackServiceTestFixtures.modpack()
        pack.versions += ModpackServiceTestFixtures.version(pack, "waiting", Modpack.Status.WAIT)
        pack.versions += ModpackServiceTestFixtures.version(pack, "building", Modpack.Status.BUILDING)
        pack.versions += ModpackServiceTestFixtures.version(pack, "ok", Modpack.Status.OK)
        val flow = mockk<FindFlow<Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        coEvery { flow.collect(any()) } coAnswers {
            val collector = firstArg<FlowCollector<Modpack>>()
            collector.emit(pack)
        }
        ModpackService.recoverUnfinishedVersionBuildsOnStartup()
        coVerify(exactly = 2) { collection.updateOne(any<Bson>(), any<Bson>(), any()) }
    }

    @Test
    fun `full rebuild succeeds through archive and client-pack stages`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(
            pack,
            "full-success",
            mods = mutableListOf(
                ModpackServiceTestFixtures.mod("status-effect-bars-reforged", side = Mod.Side.BOTH),
                ModpackServiceTestFixtures.mod("server-addon", side = Mod.Side.BOTH)
            )
        )
        val updates = mutableListOf<Bson>()
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            updates += secondArg<Bson>()
            mockk(relaxed = true)
        }
        version.storageDir.mkdirs()
        TarZstArchiveWriter(version.zstdPack).use { it.addFile("overrides/config/keep.txt", byteArrayOf(1)) }
        ModpackService.testServerModDownloadTaskFactory = { Task2.Leaf("server-download") { } }
        ModpackService.testClientModDownloadTaskFactory = { Task2.Leaf("client-download") { } }
        try {
            val task = ModpackService.createVersionBuildTaskForTest(player, pack, version, reprocessMods = true)
            task.runInline(Task2Context(emitProgress = {}))
            assertTrue(version.clientZstdPack.exists())
            assertTrue(updates.any { it.toString().contains("BUILDING") })
            assertTrue(updates.any { it.toString().contains("OK") })
            verify(exactly = 1) { MailService.changeMail(any(), match { it.contains("成功") }, any(), any()) }
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `nested rebuild failure marks fail and sends one failure mail`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, "full-failure")
        val updates = mutableListOf<Bson>()
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            updates += secondArg<Bson>()
            mockk(relaxed = true)
        }
        version.storageDir.mkdirs()
        TarZstArchiveWriter(version.zstdPack).use { it.addFile("overrides/config/keep.txt", byteArrayOf(1)) }
        ModpackService.testServerModDownloadTaskFactory = {
            Task2.Leaf("server-download-fails") { throw IllegalStateException("download failed") }
        }
        ModpackService.testClientModDownloadTaskFactory = { Task2.Leaf("client-download") { } }
        try {
            assertFailsWith<IllegalStateException> {
                ModpackService.createVersionBuildTaskForTest(player, pack, version, reprocessMods = true)
                    .runInline(Task2Context(emitProgress = {}))
            }
            assertTrue(updates.any { it.toString().contains("FAIL") })
            assertFalse(updates.any { it.toString().contains("OK") })
            verify(exactly = 1) {
                MailService.changeMail(any(), match { it.contains("失败") }, any(), any())
            }
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `buildVersion succeeds and always removes its temporary build directory`() = runTest {
        val player = ModpackServiceTestFixtures.account()
        val pack = ModpackServiceTestFixtures.modpack(player._id)
        val version = ModpackServiceTestFixtures.version(pack, "direct-build")
        val updates = mutableListOf<Bson>()
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            updates += secondArg<Bson>()
            mockk(relaxed = true)
        }
        version.storageDir.mkdirs()
        TarZstArchiveWriter(version.zstdPack).use { it.addFile("overrides/config/keep.txt", byteArrayOf(1)) }
        ModpackService.testServerModDownloadTaskFactory = { Task2.Leaf("server") { } }
        ModpackService.testClientModDownloadTaskFactory = { Task2.Leaf("client") { } }
        try {
            pack.buildVersion(version) { }
            assertTrue(version.clientZstdPack.exists())
            assertTrue(updates.any { it.toString().contains("OK") })
            assertTrue(version.storageDir.listFiles().orEmpty().none { it.name.startsWith(".build-direct-build-") })
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }

    @Test
    fun `buildVersion missing archive fails before creating build state`() = runTest {
        val pack = ModpackServiceTestFixtures.modpack()
        val version = ModpackServiceTestFixtures.version(pack, "missing-build")
        val updates = mutableListOf<Bson>()
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } coAnswers {
            updates += secondArg<Bson>()
            mockk(relaxed = true)
        }
        try {
            assertFailsWith<Exception> { pack.buildVersion(version) { } }
            assertTrue(updates.isEmpty())
            assertTrue(version.storageDir.listFiles().orEmpty().none { it.name.startsWith(".build-missing-build-") })
        } finally {
            pack.dir.deleteRecursivelyNoSymlink()
        }
    }
}
