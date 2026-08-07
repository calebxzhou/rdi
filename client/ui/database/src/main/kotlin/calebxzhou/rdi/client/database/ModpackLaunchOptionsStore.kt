package calebxzhou.rdi.client.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ModpackLaunchOptionsRecord(
    val versionId: String,
    val javaPath: String?,
    val maxMemoryMb: Int?,
    val jdwpEnabled: Boolean,
    val jdwpParam: String?,
    val customJvmParams: String = "",
    val forgeguardDisabled: Boolean = false,
)

interface ModpackLaunchOptionsStore : AutoCloseable {
    suspend fun find(versionId: String): Result<ModpackLaunchOptionsRecord?>
    suspend fun upsert(record: ModpackLaunchOptionsRecord): Result<Unit>
    suspend fun delete(versionId: String): Result<Unit>
}

internal class SqliteModpackLaunchOptionsStore(
    private val database: RClientDatabase,
    private val dispatcher: CoroutineDispatcher,
    private val mutex: Mutex
) : ModpackLaunchOptionsStore {
    override suspend fun find(versionId: String): Result<ModpackLaunchOptionsRecord?> = databaseOperation {
        database.modpackLaunchOptionsQueries.selectByVersionId(versionId) { id, javaPath, maxMemoryMb, jdwpEnabled, jdwpParam, customJvmParams, forgeguardDisabled ->
            ModpackLaunchOptionsRecord(
                versionId = id,
                javaPath = javaPath,
                maxMemoryMb = maxMemoryMb?.toInt(),
                jdwpEnabled = jdwpEnabled != 0L,
                jdwpParam = jdwpParam,
                customJvmParams = customJvmParams,
                forgeguardDisabled = forgeguardDisabled != 0L,
            )
        }.executeAsOneOrNull()
    }

    override suspend fun upsert(record: ModpackLaunchOptionsRecord): Result<Unit> = databaseOperation {
        database.modpackLaunchOptionsQueries.upsert(
            versionId = record.versionId,
            javaPath = record.javaPath,
            maxMemoryMb = record.maxMemoryMb?.toLong(),
            jdwpEnabled = if (record.jdwpEnabled) 1L else 0L,
            jdwpParam = record.jdwpParam,
            customJvmParams = record.customJvmParams,
            forgeguardDisabled = if (record.forgeguardDisabled) 1L else 0L,
        )
    }

    override suspend fun delete(versionId: String): Result<Unit> = databaseOperation {
        database.modpackLaunchOptionsQueries.deleteByVersionId(versionId)
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
