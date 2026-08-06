package calebxzhou.rdi.client.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

data class MinecraftInstallationRecord(
    val path: Path,
    val firstDiscoveredAt: Long,
    val lastSeenAt: Long
)

interface MinecraftInstallationStore : AutoCloseable {
    suspend fun list(): Result<List<MinecraftInstallationRecord>>
    suspend fun upsert(path: Path, seenAt: Long): Result<Unit>
    suspend fun delete(path: Path): Result<Unit>
    suspend fun lastFullScanAt(): Result<Long?>
    suspend fun setLastFullScanAt(scannedAt: Long): Result<Unit>
}

interface MinecraftInstallationDatabaseHandle : AutoCloseable {
    val store: MinecraftInstallationStore
    val modpackLaunchOptionsStore: ModpackLaunchOptionsStore
}

class MinecraftInstallationDatabase private constructor(
    private val driver: JdbcSqliteDriver,
    private val database: RClientDatabase,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher
) : MinecraftInstallationDatabaseHandle {
    private val operationMutex = Mutex()

    override val store: MinecraftInstallationStore =
        SqliteMinecraftInstallationStore(database, dispatcher, operationMutex)
    override val modpackLaunchOptionsStore: ModpackLaunchOptionsStore =
        SqliteModpackLaunchOptionsStore(database, dispatcher, operationMutex)
    val playerInfoStore: PlayerInfoStore =
        SqlitePlayerInfoStore(database, dispatcher, operationMutex)

    override fun close() {
        store.close()
        modpackLaunchOptionsStore.close()
        driver.close()
    }

    companion object {
        //rdi
        const val APPLICATION_ID = 0x524449L
        const val SCHEMA_VERSION = 5L

        fun open(
            file: Path,
            dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
        ): Result<MinecraftInstallationDatabase> = runCatching {
            val databaseFile = file.toAbsolutePath().normalize()
            val existed = Files.exists(databaseFile)
            require(!existed || Files.isRegularFile(databaseFile)) {
                "Minecraft installation database is not a regular file: $databaseFile"
            }
            databaseFile.parent?.let(Files::createDirectories)

            val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.toUri()}")
            try {
                if (existed) {
                    require(readPragmaLong(driver, "application_id") == APPLICATION_ID) {
                        "Unknown Minecraft installation database ownership: $databaseFile"
                    }
                    verifyIntegrity(driver, databaseFile)
                    val version = readPragmaLong(driver, "user_version")
                    require(version in 1..SCHEMA_VERSION) {
                        "Unsupported Minecraft installation database schema version $version"
                    }
                    if (version < SCHEMA_VERSION) {
                        RClientDatabase.Schema.migrate(driver, version, SCHEMA_VERSION).value
                        writePragmaLong(driver, "user_version", SCHEMA_VERSION)
                    }
                } else {
                    writePragmaLong(driver, "application_id", APPLICATION_ID)
                    RClientDatabase.Schema.create(driver).value
                    writePragmaLong(driver, "user_version", SCHEMA_VERSION)
                }

                val database = RClientDatabase(driver)
                database.minecraftInstallationQueries.selectAll().executeAsList()
                database.minecraftInstallationQueries.selectLastFullScanAt().executeAsOneOrNull()
                MinecraftInstallationDatabase(driver, database, dispatcher)
            } catch (cause: Throwable) {
                driver.close()
                throw cause
            }
        }

        private fun readPragmaLong(driver: JdbcSqliteDriver, name: String): Long =
            driver.executeQuery(null, "PRAGMA $name", { cursor ->
                if (cursor.next().value) {
                    app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0) ?: 0L)
                } else {
                    app.cash.sqldelight.db.QueryResult.Value(0L)
                }
            }, 0).value

        private fun writePragmaLong(driver: JdbcSqliteDriver, name: String, value: Long) {
            driver.execute(null, "PRAGMA $name = $value", 0)
        }

        private fun verifyIntegrity(driver: JdbcSqliteDriver, databaseFile: Path) {
            val result = driver.executeQuery(null, "PRAGMA integrity_check", { cursor ->
                if (cursor.next().value) {
                    app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0).orEmpty())
                } else {
                    app.cash.sqldelight.db.QueryResult.Value("")
                }
            }, 0).value
            require(result == "ok") { "SQLite integrity check failed for $databaseFile: $result" }
        }
    }
}

private class SqliteMinecraftInstallationStore(
    private val database: RClientDatabase,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val mutex: Mutex
) : MinecraftInstallationStore {
    override suspend fun list(): Result<List<MinecraftInstallationRecord>> = databaseOperation {
        database.minecraftInstallationQueries.selectAll { path, firstDiscoveredAt, lastSeenAt ->
            MinecraftInstallationRecord(Path.of(path), firstDiscoveredAt, lastSeenAt)
        }.executeAsList()
    }

    override suspend fun upsert(path: Path, seenAt: Long): Result<Unit> = databaseOperation {
        val normalizedPath = path.toAbsolutePath().normalize().toString()
        database.minecraftInstallationQueries.upsertInstallation(
            path = normalizedPath,
            firstDiscoveredAt = seenAt,
            lastSeenAt = seenAt
        )
    }

    override suspend fun delete(path: Path): Result<Unit> = databaseOperation {
        database.minecraftInstallationQueries.deleteByPath(path.toAbsolutePath().normalize().toString())
    }

    override suspend fun lastFullScanAt(): Result<Long?> = databaseOperation {
        database.minecraftInstallationQueries.selectLastFullScanAt().executeAsOneOrNull()
    }

    override suspend fun setLastFullScanAt(scannedAt: Long): Result<Unit> = databaseOperation {
        database.minecraftInstallationQueries.upsertLastFullScanAt(scannedAt)
    }

    override fun close() = Unit

    private suspend fun <T> databaseOperation(block: () -> T): Result<T> = withContext(dispatcher) {
        mutex.withLock {
            try {
                Result.success(block())
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                Result.failure(cause)
            }
        }
    }
}
