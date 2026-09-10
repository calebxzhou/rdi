package calebxzau.rdi.server.service.baseworld

import calebxzau.rdi.common.model.BaseWorld
import calebxzau.rdi.common.model.BaseWorldUploadSessionCreateDto
import calebxzau.rdi.common.model.BaseWorldUploadStatus
import calebxzau.rdi.common.util.uuid7j
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.common.archive.TarZstArchiveWriter
import calebxzhou.rdi.common.archive.forEachTarZstEntryStreaming
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import io.ktor.utils.io.ByteReadChannel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import org.bson.types.ObjectId
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class BaseWorldBackgroundTest {
    @Test
    fun `validation semaphore admits two worlds and then the third`() = runTest {
        val root = Files.createTempDirectory("base-world-background-capacity").toFile()
        try {
            val worlds = distinctWorlds(3)
            val scheduler = Scheduler()
            val entered = Channel<UUID>(Channel.UNLIMITED)
            val holds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
            val uploadToWorld = ConcurrentHashMap<UUID, UUID>()
            val fixture = multiWorldFixture(root, worlds, scheduler, validationConcurrency = 2) { uploadId ->
                val worldId = uploadToWorld[uploadId]!!
                holds[worldId] = CompletableDeferred()
                entered.send(worldId)
                holds[worldId]!!.await()
            }
            val sessions = worlds.associateWith { template ->
                upload(fixture.service, template, archive(root, uploadedFiles())).also { uploadToWorld[it.id] = template.id }
            }
            sessions.forEach { (template, session) ->
                assertEquals(BaseWorldUploadStatus.Queued, fixture.service.completeUpload(template.ownerId, template.id, session.id).getOrThrow().status)
            }

            val tasks = scheduler.tasks.values.toList()
            val jobs = worlds.take(2).indices.associate { index ->
                worlds[index].id to async(Dispatchers.Default) {
                    runCatching { tasks[index].action(Task2Context(emitProgress = {})) }
                }
            }
            val first = entered.receive()
            val second = entered.receive()
            assertEquals(2, setOf(first, second).size)
            val third = worlds.single { it.id != first && it.id != second }
            val thirdJob = async(Dispatchers.Default) {
                runCatching { tasks[2].action(Task2Context(emitProgress = {})) }
            }
            assertEquals(
                BaseWorldUploadStatus.Queued,
                fixture.service.uploadStatus(third.ownerId, third.id, sessions.getValue(third).id).getOrThrow().status,
            )

            holds[first]!!.complete(Unit)
            jobs.getValue(first).await()
            assertEquals(third.id, withContext(Dispatchers.Default) { withTimeout(10_000) { entered.receive() } })
            assertEquals(
                BaseWorldUploadStatus.Processing,
                fixture.service.uploadStatus(third.ownerId, third.id, sessions.getValue(third).id).getOrThrow().status,
            )
            worlds.forEach { holds[it.id]!!.complete(Unit) }
            jobs.values.awaitAll()
            thirdJob.await()
            worlds.forEach { template ->
                assertEquals(BaseWorldUploadStatus.Ready, fixture.service.uploadStatus(template.ownerId, template.id, sessions.getValue(template).id).getOrThrow().status)
            }
            fixture.service.shutdown()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `validation permit is released after failure and waiting cancellation`() = runTest {
        val root = Files.createTempDirectory("base-world-background-permit-release").toFile()
        try {
            val worlds = distinctWorlds(3)
            val scheduler = Scheduler()
            val entered = Channel<UUID>(Channel.UNLIMITED)
            val holds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
            val uploadToWorld = ConcurrentHashMap<UUID, UUID>()
            val fixture = multiWorldFixture(root, worlds, scheduler, validationConcurrency = 1) { uploadId ->
                val worldId = uploadToWorld[uploadId]!!
                holds[worldId] = CompletableDeferred()
                entered.send(worldId)
                holds[worldId]!!.await()
            }
            val invalid = upload(fixture.service, worlds[0], archive(root, mapOf("region/only.mca" to byteArrayOf(1))))
            val valid = upload(fixture.service, worlds[1], archive(root, uploadedFiles()))
            uploadToWorld[invalid.id] = worlds[0].id
            uploadToWorld[valid.id] = worlds[1].id
            fixture.service.completeUpload(worlds[0].ownerId, worlds[0].id, invalid.id).getOrThrow()
            fixture.service.completeUpload(worlds[1].ownerId, worlds[1].id, valid.id).getOrThrow()
            val tasks = scheduler.tasks.values.toList()
            val invalidJob = async(Dispatchers.Default) {
                runCatching { tasks[0].action(Task2Context(emitProgress = {})) }
            }
            assertEquals(worlds[0].id, entered.receive())
            assertEquals(BaseWorldUploadStatus.Queued, fixture.service.uploadStatus(worlds[1].ownerId, worlds[1].id, valid.id).getOrThrow().status)
            val validJob = async(Dispatchers.Default) {
                runCatching { tasks[1].action(Task2Context(emitProgress = {})) }
            }
            assertEquals(BaseWorldUploadStatus.Queued, fixture.service.uploadStatus(worlds[1].ownerId, worlds[1].id, valid.id).getOrThrow().status)
            holds[worlds[0].id]!!.complete(Unit)
            assertTrue(invalidJob.await().isFailure)
            assertEquals(worlds[1].id, withContext(Dispatchers.Default) { withTimeout(10_000) { entered.receive() } })
            assertEquals(BaseWorldUploadStatus.Processing, fixture.service.uploadStatus(worlds[1].ownerId, worlds[1].id, valid.id).getOrThrow().status)
            holds[worlds[1].id]!!.complete(Unit)
            assertTrue(validJob.await().isSuccess)
            assertEquals(BaseWorldUploadStatus.Failed, fixture.service.uploadStatus(worlds[0].ownerId, worlds[0].id, invalid.id).getOrThrow().status)
            assertEquals(BaseWorldUploadStatus.Ready, fixture.service.uploadStatus(worlds[1].ownerId, worlds[1].id, valid.id).getOrThrow().status)
            fixture.service.shutdown()

            val cancelRoot = Files.createTempDirectory("base-world-background-wait-cancel").toFile()
            try {
                val cancelWorlds = distinctWorlds(3)
                val cancelScheduler = Scheduler()
                val cancelEntered = Channel<UUID>(Channel.UNLIMITED)
                val cancelHolds = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
                val cancelUploadToWorld = ConcurrentHashMap<UUID, UUID>()
                val cancelFixture = multiWorldFixture(cancelRoot, cancelWorlds, cancelScheduler, validationConcurrency = 1) { uploadId ->
                    val worldId = cancelUploadToWorld[uploadId]!!
                    cancelHolds[worldId] = CompletableDeferred()
                    cancelEntered.send(worldId)
                    cancelHolds[worldId]!!.await()
                }
                val cancelSessions = cancelWorlds.map { template ->
                    upload(cancelFixture.service, template, archive(cancelRoot, uploadedFiles())).also { cancelUploadToWorld[it.id] = template.id }
                }
                cancelSessions.forEachIndexed { index, session ->
                    cancelFixture.service.completeUpload(cancelWorlds[index].ownerId, cancelWorlds[index].id, session.id).getOrThrow()
                }
                val cancelTasks = cancelScheduler.tasks.values.toList()
                val firstJob = async(Dispatchers.Default) {
                    runCatching { cancelTasks[0].action(Task2Context(emitProgress = {})) }
                }
                assertEquals(cancelWorlds[0].id, cancelEntered.receive())
                val waitingJob = async(Dispatchers.Default) {
                    runCatching { cancelTasks[1].action(Task2Context(emitProgress = {})) }
                }
                waitingJob.cancelAndJoin()
                assertEquals(BaseWorldUploadStatus.Queued, cancelFixture.service.uploadStatus(cancelWorlds[1].ownerId, cancelWorlds[1].id, cancelSessions[1].id).getOrThrow().status)
                val thirdJob = async(Dispatchers.Default) {
                    runCatching { cancelTasks[2].action(Task2Context(emitProgress = {})) }
                }
                cancelHolds[cancelWorlds[0].id]!!.complete(Unit)
                firstJob.await()
                assertEquals(cancelWorlds[2].id, withContext(Dispatchers.Default) { withTimeout(10_000) { cancelEntered.receive() } })
                cancelHolds[cancelWorlds[2].id]!!.complete(Unit)
                thirdJob.await()
                assertEquals(BaseWorldUploadStatus.Ready, cancelFixture.service.uploadStatus(cancelWorlds[2].ownerId, cancelWorlds[2].id, cancelSessions[2].id).getOrThrow().status)
                cancelFixture.service.shutdown()
            } finally {
                cancelRoot.deleteRecursively()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `completion queues once and background task publishes archive only`() = runTest {
        val root = Files.createTempDirectory("base-world-background-acceptance").toFile()
        try {
            val world = world()
            val scheduler = Scheduler()
            val mail = mutableListOf<Mail>()
            val fixture = fixture(root, world, scheduler, mail)
            val files = uploadedFiles()
            val archive = archive(root, files)
            val session = upload(fixture.service, world, archive)

            val queued = fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            assertEquals(BaseWorldUploadStatus.Queued, queued.status)
            assertEquals(1, scheduler.tasks.size)
            assertFalse(fixture.destination(world).exists())
            assertTrue(mail.isEmpty())

            assertEquals(
                BaseWorldUploadStatus.Queued,
                fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow().status,
            )
            assertEquals(1, scheduler.tasks.size)

            scheduler.runOnlyTask()
            val ready = fixture.service.uploadStatus(world.ownerId, world.id, session.id).getOrThrow()
            assertEquals(BaseWorldUploadStatus.Ready, ready.status)
            assertEquals(files.values.sumOf { it.size.toLong() }, fixture.worlds[world.id]!!.size)
            assertEquals(setOf("world.tar.zst"), fixture.destination(world).walkTopDown().filter(File::isFile).map { it.relativeTo(fixture.destination(world)).path }.toSet())
            val storedFiles = readArchive(fixture.destination(world).resolve("world.tar.zst"))
            assertEquals(files.keys, storedFiles.keys)
            files.forEach { (path, bytes) -> assertContentEquals(bytes, storedFiles[path]) }
            assertEquals(1, mail.size)
            assertEquals(world.ownerId, mail.single().recipient)
            assertTrue(mail.single().title.contains("准备好"))

            assertEquals(BaseWorldUploadStatus.Ready, fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow().status)
            assertEquals(1, scheduler.tasks.size)
            assertEquals(1, mail.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed notification is retried after reload and persisted notified flag prevents duplicates`() = runTest {
        val root = Files.createTempDirectory("base-world-background-mail").toFile()
        try {
            val world = world()
            val scheduler = Scheduler()
            val failedMail = mutableListOf<Mail>()
            val fixture = fixture(root, world, scheduler, failedMail, notifier = { _, _, _ -> throw IllegalStateException("mail unavailable") })
            val session = upload(fixture.service, world, archive(root, uploadedFiles()))
            fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            scheduler.runOnlyTask()
            assertEquals(BaseWorldUploadStatus.Ready, fixture.service.uploadStatus(world.ownerId, world.id, session.id).getOrThrow().status)
            assertTrue(failedMail.isEmpty())

            val recoveredMail = mutableListOf<Mail>()
            val recoveredScheduler = Scheduler()
            val recovered = fixture.service(recoveredScheduler, recoveredMail)
            recovered.startRecovery().join()
            assertEquals(true, recoveredMail.singleOrNull()?.title?.contains("准备好"))
            assertEquals(world.ownerId, recoveredMail.single().recipient)

            val secondRecoveryMail = mutableListOf<Mail>()
            val secondRecovery = fixture.service(Scheduler(), secondRecoveryMail)
            secondRecovery.startRecovery().join()
            assertTrue(secondRecoveryMail.isEmpty())
            recovered.shutdown()
            secondRecovery.shutdown()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `queued and processing sessions are recovered and scheduled after restart`() = runTest {
        val root = Files.createTempDirectory("base-world-background-recovery").toFile()
        try {
            val world = world()
            val scheduler = Scheduler()
            val fixture = fixture(root, world, scheduler, mutableListOf())
            val first = upload(fixture.service, world, archive(root, uploadedFiles()))
            fixture.service.completeUpload(world.ownerId, world.id, first.id).getOrThrow()
            fixture.service.shutdown()

            val queuedScheduler = Scheduler()
            val queuedService = fixture.service(queuedScheduler, mutableListOf())
            queuedService.startRecovery().join()
            assertEquals(1, queuedScheduler.tasks.size)
            queuedScheduler.runOnlyTask()
            assertEquals(BaseWorldUploadStatus.Ready, queuedService.uploadStatus(world.ownerId, world.id, first.id).getOrThrow().status)
            queuedService.shutdown()

            val processingRoot = Files.createTempDirectory("base-world-background-processing-recovery").toFile()
            try {
                val processingWorld = world()
                val processingFixture = fixture(processingRoot, processingWorld, Scheduler(), mutableListOf())
                val second = upload(
                    processingFixture.service,
                    processingWorld,
                    archive(processingRoot, uploadedFiles().mapValues { (_, value) -> value.map { (it + 1).toByte() }.toByteArray() }),
                )
                val secondStore = BaseWorldUploadStore(processingRoot, partSize = 11)
                secondStore.acceptForCompletion(processingWorld.ownerId, processingWorld.id, second.id)
                secondStore.markProcessing(processingWorld.ownerId, processingWorld.id, second.id)
                processingFixture.service.shutdown()

                val processingScheduler = Scheduler()
                val processingService = BaseWorldService(
                    database = processingFixture.database,
                    repository = processingFixture.repository,
                    uploads = BaseWorldUploadStore(processingRoot, partSize = 11),
                    worldDestination = processingFixture.destination,
                    taskSubmitter = processingScheduler::submit,
                    taskStarter = {},
                    notifier = { _, _, _ -> },
                    taskCanceller = {},
                )
                processingService.startRecovery().join()
                assertEquals(1, processingScheduler.tasks.size)
                processingScheduler.runOnlyTask()
                assertEquals(BaseWorldUploadStatus.Ready, processingService.uploadStatus(processingWorld.ownerId, processingWorld.id, second.id).getOrThrow().status)
                processingService.shutdown()
            } finally {
                processingRoot.deleteRecursively()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `processing recovery skips enqueue when queued normalization fails`() = runTest {
        val root = Files.createTempDirectory("base-world-background-recovery-failure").toFile()
        try {
            val world = world()
            val scheduler = Scheduler()
            val fixture = fixture(root, world, scheduler, mutableListOf())
            val session = upload(fixture.service, world, archive(root, uploadedFiles()))
            fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            val store = BaseWorldUploadStore(root, partSize = 11)
            store.acceptForCompletion(world.ownerId, world.id, session.id)
            store.markProcessing(world.ownerId, world.id, session.id)

            val recoveredScheduler = Scheduler()
            val recovered = BaseWorldService(
                database = fixture.database,
                repository = fixture.repository,
                uploads = BaseWorldUploadStore(root, partSize = 11),
                worldDestination = fixture.destination,
                taskSubmitter = recoveredScheduler::submit,
                taskStarter = {},
                notifier = { _, _, _ -> },
                taskCanceller = {},
                restoreQueuedForRecovery = { _, _, _ -> throw IllegalStateException("persistence unavailable") },
            )
            recovered.startRecovery().join()
            assertTrue(recoveredScheduler.tasks.isEmpty())
            assertEquals(
                BaseWorldUploadStatus.Processing,
                recovered.uploadStatus(world.ownerId, world.id, session.id).getOrThrow().status,
            )
            recovered.shutdown()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `processing recovery skips enqueue when status becomes terminal after normalization`() = runTest {
        val root = Files.createTempDirectory("base-world-background-recovery-terminal-race").toFile()
        try {
            val world = world()
            val fixture = fixture(root, world, Scheduler(), mutableListOf())
            val session = upload(fixture.service, world, archive(root, uploadedFiles()))
            fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            val store = BaseWorldUploadStore(root, partSize = 11)
            store.acceptForCompletion(world.ownerId, world.id, session.id)
            store.markProcessing(world.ownerId, world.id, session.id)

            val recoveredScheduler = Scheduler()
            val recovered = BaseWorldService(
                database = fixture.database,
                repository = fixture.repository,
                uploads = BaseWorldUploadStore(root, partSize = 11),
                worldDestination = fixture.destination,
                taskSubmitter = recoveredScheduler::submit,
                taskStarter = {},
                notifier = { _, _, _ -> },
                taskCanceller = {},
                restoreQueuedForRecovery = { owner, worldId, uploadId ->
                    store.restoreQueued(owner, worldId, uploadId)
                    store.markTerminal(owner, worldId, uploadId, BaseWorldUploadStatus.Ready)
                },
            )
            recovered.startRecovery().join()
            assertTrue(recoveredScheduler.tasks.isEmpty())
            assertEquals(
                BaseWorldUploadStatus.Ready,
                recovered.uploadStatus(world.ownerId, world.id, session.id).getOrThrow().status,
            )
            recovered.shutdown()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `queue submission failure restores uploading and retry succeeds`() = runTest {
        val root = Files.createTempDirectory("base-world-background-submit-failure").toFile()
        try {
            val world = world()
            val scheduler = Scheduler(failSubmission = true)
            val fixture = fixture(root, world, scheduler, mutableListOf())
            val session = upload(fixture.service, world, archive(root, uploadedFiles()))
            assertTrue(fixture.service.completeUpload(world.ownerId, world.id, session.id).isFailure)
            assertEquals(BaseWorldUploadStatus.Uploading, fixture.service.uploadStatus(world.ownerId, world.id, session.id).getOrThrow().status)
            scheduler.failSubmission = false
            assertEquals(BaseWorldUploadStatus.Queued, fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow().status)
            assertEquals(1, scheduler.tasks.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `invalid archive fails in background and preserves unpublished world files`() = runTest {
        val root = Files.createTempDirectory("base-world-background-invalid").toFile()
        try {
            val world = world(size = 42)
            val scheduler = Scheduler()
            val mail = mutableListOf<Mail>()
            val fixture = fixture(root, world, scheduler, mail)
            val destination = fixture.destination(world)
            destination.mkdirs()
            val oldWorldFile = byteArrayOf(7, 6, 5)
            destination.resolve("old.txt").writeBytes(oldWorldFile)
            val invalid = archive(root, mapOf("region/r.0.0.mca" to byteArrayOf(1, 2, 3)))
            val session = upload(fixture.service, world, invalid)
            fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            assertFailsWith<Throwable> { scheduler.runOnlyTask() }
            val failed = fixture.service.uploadStatus(world.ownerId, world.id, session.id).getOrThrow()
            assertEquals(BaseWorldUploadStatus.Failed, failed.status)
            assertTrue(!failed.errorMessage.isNullOrBlank())
            assertContentEquals(oldWorldFile, destination.resolve("old.txt").readBytes())
            assertFalse(destination.resolve("world.tar.zst").exists())
            assertEquals(world.ownerId, mail.single().recipient)
            assertTrue(mail.single().title.contains("失败"))

            val retrySession = fixture.service.createUpload(
                world.ownerId,
                world.id,
                BaseWorldUploadSessionCreateDto(invalid.length(), sha1(invalid.readBytes())),
            ).getOrThrow()
            assertNotNull(retrySession)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `accepted pending session rejects mutation and deletion until processing completes`() = runTest {
        val root = Files.createTempDirectory("base-world-background-locks").toFile()
        try {
            val world = world()
            val scheduler = Scheduler()
            val fixture = fixture(root, world, scheduler, mutableListOf())
            val archive = archive(root, uploadedFiles())
            val session = upload(fixture.service, world, archive)
            fixture.service.completeUpload(world.ownerId, world.id, session.id).getOrThrow()
            val part = archive.readBytes().copyOfRange(0, minOf(session.partSize, archive.length().toInt()))
            assertTrue(fixture.service.uploadPart(world.ownerId, world.id, session.id, 0, part.size.toLong(), sha1(part), ByteReadChannel(part)).isFailure)
            assertTrue(fixture.service.cancelUpload(world.ownerId, world.id, session.id).isFailure)
            assertTrue(fixture.service.delete(world.ownerId, world.id).isFailure)
            scheduler.runOnlyTask()
            assertTrue(fixture.service.delete(world.ownerId, world.id).getOrThrow())
            assertFalse(fixture.destination(world).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun upload(service: BaseWorldService, world: BaseWorld, archive: File) =
        service.createUpload(
            world.ownerId,
            world.id,
            BaseWorldUploadSessionCreateDto(archive.length(), sha1(archive.readBytes())),
        ).getOrThrow().also { session ->
            val bytes = archive.readBytes()
            for (index in 0 until session.partCount) {
                val start = index * session.partSize
                val part = bytes.copyOfRange(start, minOf(bytes.size, start + session.partSize))
                service.uploadPart(world.ownerId, world.id, session.id, index, part.size.toLong(), sha1(part), ByteReadChannel(part)).getOrThrow()
            }
        }

    private fun fixture(
        root: File,
        world: BaseWorld,
        scheduler: Scheduler,
        mail: MutableList<Mail>,
        notifier: suspend (UUID, String, String) -> Unit = { receiver, title, content -> mail += Mail(receiver, title, content) },
    ): Fixture {
        val worlds = linkedMapOf(world.id to world)
        val repository = mockk<PgBaseWorldRepo>()
        every { repository.findById(any(), any()) } answers {
            val owner = firstArg<UUID>()
            val id = secondArg<UUID>()
            worlds[id]?.takeIf { it.ownerId == owner }
        }
        every { repository.updateSize(any(), any(), any()) } answers {
            val owner = firstArg<UUID>()
            val id = secondArg<UUID>()
            val size = thirdArg<Long>()
            worlds[id]?.takeIf { it.ownerId == owner }?.copy(size = size)?.also { worlds[id] = it }
        }
        every { repository.delete(any(), any()) } answers { worlds.remove(secondArg<UUID>()) != null }
        val database = mockk<DatabaseProvider>()
        coEvery { database.transaction<Any?>(any()) } coAnswers {
            firstArg<JdbcTransaction.() -> Any?>().invoke(mockk(relaxed = true))
        }
        fun createService() = BaseWorldService(
            database = database,
            repository = repository,
            uploads = BaseWorldUploadStore(root, partSize = 11),
            worldDestination = { root.resolve("base-worlds").resolve(it.id.toString()) },
            taskSubmitter = scheduler::submit,
            taskStarter = {},
            notifier = notifier,
            taskCanceller = {},
        )
        return Fixture(
            createService(),
            worlds,
            database,
            repository,
            root,
            { root.resolve("base-worlds").resolve(it.id.toString()) },
        ) { scheduler, mail, customNotifier ->
            BaseWorldService(
                database = database,
                repository = repository,
                uploads = BaseWorldUploadStore(root, partSize = 11),
                worldDestination = { root.resolve("base-worlds").resolve(it.id.toString()) },
                taskSubmitter = scheduler::submit,
                taskStarter = {},
                notifier = customNotifier ?: { receiver, title, content -> mail += Mail(receiver, title, content) },
                taskCanceller = {},
            )
        }
    }

    private fun multiWorldFixture(
        root: File,
        worlds: List<BaseWorld>,
        scheduler: Scheduler,
        validationConcurrency: Int,
        afterMarkProcessing: suspend (UUID) -> Unit,
    ): MultiWorldFixture {
        val values = worlds.associateBy { it.id }.toMutableMap()
        val repository = mockk<PgBaseWorldRepo>()
        every { repository.findById(any(), any()) } answers {
            values[secondArg<UUID>()]?.takeIf { it.ownerId == firstArg<UUID>() }
        }
        every { repository.updateSize(any(), any(), any()) } answers {
            values[secondArg<UUID>()]?.takeIf { it.ownerId == firstArg<UUID>() }?.copy(size = thirdArg<Long>())
                ?.also { values[it.id] = it }
        }
        every { repository.delete(any(), any()) } answers { values.remove(secondArg<UUID>()) != null }
        val database = mockk<DatabaseProvider>()
        coEvery { database.transaction<Any?>(any()) } coAnswers {
            firstArg<JdbcTransaction.() -> Any?>().invoke(mockk(relaxed = true))
        }
        return MultiWorldFixture(
            service = BaseWorldService(
                database = database,
                repository = repository,
                uploads = BaseWorldUploadStore(root, partSize = 11),
                worldDestination = { root.resolve("base-worlds").resolve(it.id.toString()) },
                taskSubmitter = scheduler::submit,
                taskStarter = {},
                notifier = { _, _, _ -> },
                taskCanceller = {},
                validationConcurrency = validationConcurrency,
                afterMarkProcessing = afterMarkProcessing,
            ),
            worlds = values,
        )
    }

    private fun distinctWorlds(count: Int): List<BaseWorld> {
        val result = mutableListOf<BaseWorld>()
        val buckets = mutableSetOf<Int>()
        while (result.size < count) {
            val candidate = world()
            if (buckets.add((candidate.id.hashCode() and Int.MAX_VALUE) % 256)) result += candidate
        }
        return result
    }

    private data class Fixture(
        val service: BaseWorldService,
        val worlds: MutableMap<UUID, BaseWorld>,
        val database: DatabaseProvider,
        val repository: PgBaseWorldRepo,
        val root: File,
        val destination: (BaseWorld) -> File,
        private val serviceFactory: (Scheduler, MutableList<Mail>, (suspend (UUID, String, String) -> Unit)?) -> BaseWorldService,
    ) {
        fun service(scheduler: Scheduler, mail: MutableList<Mail>): BaseWorldService = serviceFactory(scheduler, mail, null)

    }

    private data class MultiWorldFixture(
        val service: BaseWorldService,
        val worlds: MutableMap<UUID, BaseWorld>,
    )

    private class Scheduler(var failSubmission: Boolean = false) {
        val tasks = linkedMapOf<String, Task2.Leaf>()
        private var nextId = 0
        fun submit(task: Task2, dedupeKey: String): String {
            check(dedupeKey.isNotBlank())
            check(!failSubmission) { "queue unavailable" }
            val id = "run-${++nextId}"
            tasks[id] = task as Task2.Leaf
            return id
        }
        suspend fun runOnlyTask() {
            val task = tasks.values.last()
            task.action(Task2Context(emitProgress = {}))
        }
    }

    private data class Mail(val recipient: UUID, val title: String, val content: String)

    private fun world(size: Long = 0): BaseWorld = BaseWorld(ObjectId().toUUID(), ObjectId().toUUID(), "Test world", "normal", null, size)

    private fun uploadedFiles() = linkedMapOf(
        "level.dat" to byteArrayOf(1, 2, 3),
        "region/r.0.0.mca" to byteArrayOf(4, 5, 6, 7),
        "data/raids.dat" to byteArrayOf(8, 9),
    )

    private fun archive(root: File, files: Map<String, ByteArray>): File {
        val target = root.resolve("source-${uuid7j()}.tar.zst")
        TarZstArchiveWriter(target).use { writer ->
            writer.addDirectory("region")
            writer.addDirectory("data")
            files.forEach { (path, bytes) -> writer.addFile(path, bytes) }
        }
        return target
    }

    private fun readArchive(archive: File): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        forEachTarZstEntryStreaming(archive) { entry, input ->
            if (!entry.isDirectory) files[entry.path] = input.readBytes()
        }
        return files
    }

    private fun sha1(bytes: ByteArray): String = bytes.sha1
}
