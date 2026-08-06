package calebxzhou.rdi.client.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

data class PlayerInfoRecord(
    val playerId: String,
    val name: String,
    val isSlim: Boolean,
    val skinUrl: String,
    val capeUrl: String?,
    val updatedAt: Long
)

interface PlayerInfoStore {
    suspend fun findByIds(ids: Collection<String>): Result<Map<String, PlayerInfoRecord>>
    suspend fun upsertAll(records: Collection<PlayerInfoRecord>): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

internal class SqlitePlayerInfoStore(
    private val database: RClientDatabase,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
    private val mutex: Mutex = Mutex()
) : PlayerInfoStore {
    override suspend fun findByIds(ids: Collection<String>): Result<Map<String, PlayerInfoRecord>> {
        val normalizedIds = ids.map(::normalizeId).distinct()
        if (normalizedIds.isEmpty()) return Result.success(emptyMap())
        return databaseOperation {
            database.playerInfoQueries.selectByIds(normalizedIds) { playerId, name, isSlim, skinUrl, capeUrl, updatedAt ->
                PlayerInfoRecord(playerId, name, isSlim != 0L, skinUrl, capeUrl, updatedAt)
            }.executeAsList().associateBy { it.playerId }
        }
    }

    override suspend fun upsertAll(records: Collection<PlayerInfoRecord>): Result<Unit> {
        if (records.isEmpty()) return Result.success(Unit)
        return databaseOperation {
            database.transaction {
                records.forEach { record ->
                    database.playerInfoQueries.upsert(
                        playerId = normalizeId(record.playerId),
                        name = record.name,
                        isSlim = if (record.isSlim) 1L else 0L,
                        skinUrl = record.skinUrl,
                        capeUrl = record.capeUrl,
                        updatedAt = record.updatedAt
                    )
                }
            }
        }
    }

    override suspend fun delete(id: String): Result<Unit> = databaseOperation {
        database.playerInfoQueries.deleteById(normalizeId(id))
    }

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

    private fun normalizeId(id: String): String = id.lowercase(Locale.ROOT)
}
