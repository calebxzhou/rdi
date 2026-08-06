package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.database.MinecraftInstallationDatabaseHandle
import calebxzhou.rdi.client.database.MinecraftInstallationRecord
import calebxzhou.rdi.client.database.MinecraftInstallationStore
import calebxzhou.rdi.client.database.ModpackLaunchOptionsRecord
import calebxzhou.rdi.client.database.ModpackLaunchOptionsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalMinecraftDiscoveryServiceTest {
    @Test
    fun `valid stored rows update last seen and missing rows are deleted`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val valid = root.resolve("valid")
        val missing = root.resolve("missing")
        val store = MemoryStore(
            initialRecords = listOf(
                MinecraftInstallationRecord(valid, 1L, 2L),
                MinecraftInstallationRecord(missing, 3L, 4L)
            )
        )
        val validator = MapValidator(
            stored = mapOf(valid to MinecraftInstallationValidation.Valid(valid))
        )
        val clock = FixedClock(100L)

        runService(store, validator, clock)

        assertEquals(100L, store.records.single { it.path == valid }.lastSeenAt)
        assertTrue(store.records.none { it.path == missing })
    }

    @Test
    fun `inaccessible stored rows remain unchanged`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val inaccessible = root.resolve("inaccessible")
        val store = MemoryStore(listOf(MinecraftInstallationRecord(inaccessible, 1L, 2L)))
        val validator = MapValidator(
            stored = mapOf(inaccessible to MinecraftInstallationValidation.Inaccessible)
        )

        runService(store, validator, FixedClock(100L))

        assertEquals(listOf(MinecraftInstallationRecord(inaccessible, 1L, 2L)), store.records)
    }

    @Test
    fun `known APPDATA and USERPROFILE candidates are checked without RDI fallback`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val appDataCandidate = root.resolve("appdata/.minecraft")
        val userProfileCandidate = root.resolve("profile/.minecraft")
        val store = MemoryStore()
        val validator = MapValidator(
            discovered = mapOf(
                appDataCandidate to MinecraftInstallationValidation.Valid(appDataCandidate),
                userProfileCandidate to MinecraftInstallationValidation.Valid(userProfileCandidate)
            )
        )

        runService(
            store = store,
            validator = validator,
            clock = FixedClock(100L),
            environment = mapOf(
                "APPDATA" to appDataCandidate.parent.toString(),
                "USERPROFILE" to userProfileCandidate.parent.toString()
            )
        )

        assertEquals(setOf(appDataCandidate, userProfileCandidate), store.records.map { it.path }.toSet())
        assertTrue(store.records.none { it.path == Path.of(System.getProperty("user.dir"), ".minecraft") })
    }

    @Test
    fun `full scan runs when timestamp is absent and updates timestamp after success`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val found = root.resolve("found")
        val store = MemoryStore()
        val scanCount = AtomicInteger()
        val scanner = fakeScanner { _, onFound ->
            scanCount.incrementAndGet()
            onFound(found)
            Result.success(listOf(found))
        }

        runService(store, MapValidator(), FixedClock(100L), scanner = scanner)

        assertEquals(1, scanCount.get())
        assertEquals(100L, store.lastFullScanAt)
        assertEquals(found, store.records.single().path)
    }

    @Test
    fun `full scan does not run before 72 hour boundary`() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore(lastFullScanAt = 0L)
        val scanCount = AtomicInteger()
        val scanner = fakeScanner { _, _ ->
            scanCount.incrementAndGet()
            Result.success(emptyList())
        }

        runService(store, MapValidator(), FixedClock(FULL_SCAN_INTERVAL_MS - 1), scanner = scanner)

        assertEquals(0, scanCount.get())
        assertEquals(0L, store.lastFullScanAt)
    }

    @Test
    fun `full scan runs exactly at 72 hour boundary`() = kotlinx.coroutines.runBlocking {
        val store = MemoryStore(lastFullScanAt = 0L)
        val scanCount = AtomicInteger()
        val scanner = fakeScanner { _, _ ->
            scanCount.incrementAndGet()
            Result.success(emptyList())
        }

        runService(store, MapValidator(), FixedClock(FULL_SCAN_INTERVAL_MS), scanner = scanner)

        assertEquals(1, scanCount.get())
        assertEquals(FULL_SCAN_INTERVAL_MS, store.lastFullScanAt)
    }

    @Test
    fun `failed full scan keeps incremental discoveries but not timestamp`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val found = root.resolve("found")
        val store = MemoryStore()
        val scanner = fakeScanner { _, onFound ->
            onFound(found)
            Result.failure(IllegalStateException("scan failed"))
        }

        runService(store, MapValidator(), FixedClock(100L), scanner = scanner)

        assertEquals(listOf(found), store.records.map { it.path })
        assertEquals(null, store.lastFullScanAt)
    }

    @Test
    fun `database initialization failure skips discovery`() = kotlinx.coroutines.runBlocking {
        val scanCount = AtomicInteger()
        val fixedDriveCount = AtomicInteger()
        val scanner = fakeScanner { _, _ ->
            scanCount.incrementAndGet()
            Result.success(emptyList())
        }
        val service = LocalMinecraftDiscoveryService(
            databasePath = Path.of("data.db"),
            fixedDriveProvider = FixedDriveProvider {
                fixedDriveCount.incrementAndGet()
                Result.success(emptyList())
            },
            scanner = scanner,
            platformSupported = { true },
            databaseOpener = { Result.failure(IllegalStateException("database failed")) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        service.start()!!.join()
        service.close()

        assertEquals(0, fixedDriveCount.get())
        assertEquals(0, scanCount.get())
    }

    @Test
    fun `concurrent starts return one discovery job`() = kotlinx.coroutines.runBlocking {
        val scanGate = CompletableDeferred<Unit>()
        val scanCount = AtomicInteger()
        val store = MemoryStore()
        val handle = MemoryDatabaseHandle(store)
        val scanner = fakeScanner { _, _ ->
            scanCount.incrementAndGet()
            scanGate.await()
            Result.success(emptyList())
        }
        val service = LocalMinecraftDiscoveryService(
            databasePath = Path.of("data.db"),
            fixedDriveProvider = FixedDriveProvider { Result.success(listOf(Path.of("C:/"))) },
            scanner = scanner,
            platformSupported = { true },
            databaseOpener = { Result.success(handle) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )

        val first = service.start()!!
        assertSame(first, service.start())
        scanGate.complete(Unit)
        first.join()
        service.close()

        assertEquals(1, scanCount.get())
    }

    @Test
    fun `installations flow reflects valid stored records and full scan discoveries`() = kotlinx.coroutines.runBlocking {
        val root = Files.createTempDirectory("minecraft-discovery")
        val valid = root.resolve("valid")
        val missing = root.resolve("missing")
        val found = root.resolve("found")
        val store = MemoryStore(
            initialRecords = listOf(
                MinecraftInstallationRecord(valid, 1L, 2L),
                MinecraftInstallationRecord(missing, 3L, 4L)
            )
        )
        val validator = MapValidator(
            stored = mapOf(valid to MinecraftInstallationValidation.Valid(valid))
        )
        val scanner = fakeScanner { _, onFound ->
            onFound(found)
            Result.success(listOf(found))
        }
        val handle = MemoryDatabaseHandle(store)
        val service = LocalMinecraftDiscoveryService(
            databasePath = Path.of("data.db"),
            fixedDriveProvider = FixedDriveProvider { Result.success(listOf(Path.of("C:/"))) },
            validator = validator,
            scanner = scanner,
            clock = FixedClock(100L),
            environment = { null },
            platformSupported = { true },
            databaseOpener = { Result.success(handle) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
        service.start()!!.join()

        assertEquals(setOf(valid, found), service.installations.value.toSet())
        service.close()
    }

    private suspend fun runService(
        store: MemoryStore,
        validator: MapValidator,
        clock: FixedClock,
        scanner: MinecraftInstallationScanner = fakeScanner { _, _ -> Result.success(emptyList()) },
        environment: Map<String, String> = emptyMap()
    ) {
        val handle = MemoryDatabaseHandle(store)
        val service = LocalMinecraftDiscoveryService(
            databasePath = Path.of("data.db"),
            fixedDriveProvider = FixedDriveProvider { Result.success(listOf(Path.of("C:/"))) },
            validator = validator,
            scanner = scanner,
            clock = clock,
            environment = environment::get,
            platformSupported = { true },
            databaseOpener = { Result.success(handle) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
        service.start()!!.join()
        service.close()
    }

    private fun fakeScanner(
        block: suspend (List<Path>, suspend (Path) -> Unit) -> Result<List<Path>>
    ) = object : MinecraftInstallationScanner {
        override suspend fun scan(roots: List<Path>, onFound: suspend (Path) -> Unit): Result<List<Path>> =
            block(roots, onFound)
    }

    private class FixedClock(private val value: Long) : MinecraftClock {
        override fun now(): Long = value
    }

    private class MapValidator(
        private val discovered: Map<Path, MinecraftInstallationValidation> = emptyMap(),
        private val stored: Map<Path, MinecraftInstallationValidation> = emptyMap()
    ) : MinecraftInstallationValidator {
        override fun validateDiscoveredCandidate(candidate: Path, fixedDriveRoots: List<Path>) =
            discovered[candidate] ?: MinecraftInstallationValidation.Missing

        override fun validateStoredRealPath(realPath: Path, fixedDriveRoots: List<Path>) =
            stored[realPath] ?: MinecraftInstallationValidation.Missing
    }

    private class MemoryDatabaseHandle(override val store: MemoryStore) : MinecraftInstallationDatabaseHandle {
        override val modpackLaunchOptionsStore: ModpackLaunchOptionsStore = EmptyModpackLaunchOptionsStore
        override fun close() = Unit
    }

    private object EmptyModpackLaunchOptionsStore : ModpackLaunchOptionsStore {
        override suspend fun find(versionId: String): Result<ModpackLaunchOptionsRecord?> = Result.success(null)

        override suspend fun upsert(record: ModpackLaunchOptionsRecord): Result<Unit> = Result.success(Unit)

        override suspend fun delete(versionId: String): Result<Unit> = Result.success(Unit)

        override fun close() = Unit
    }

    private class MemoryStore(
        initialRecords: List<MinecraftInstallationRecord> = emptyList(),
        var lastFullScanAt: Long? = null
    ) : MinecraftInstallationStore {
        var records: List<MinecraftInstallationRecord> = initialRecords
            private set

        override suspend fun list(): Result<List<MinecraftInstallationRecord>> = Result.success(records)

        override suspend fun upsert(path: Path, seenAt: Long): Result<Unit> {
            val existing = records.firstOrNull { it.path == path }
            val replacement = MinecraftInstallationRecord(path, existing?.firstDiscoveredAt ?: seenAt, seenAt)
            records = records.filterNot { it.path == path } + replacement
            return Result.success(Unit)
        }

        override suspend fun delete(path: Path): Result<Unit> {
            records = records.filterNot { it.path == path }
            return Result.success(Unit)
        }

        override suspend fun lastFullScanAt(): Result<Long?> = Result.success(lastFullScanAt)

        override suspend fun setLastFullScanAt(scannedAt: Long): Result<Unit> {
            lastFullScanAt = scannedAt
            return Result.success(Unit)
        }

        override fun close() = Unit
    }

    private companion object {
        const val FULL_SCAN_INTERVAL_MS = 72L * 60 * 60 * 1000
    }
}
