package calebxzhou.rdi.client.service

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * A multiplatform, batch-fetching, in-memory cache keyed by String IDs.
 * Replaces Caffeine (JVM-only) with simple time-based expiration.
 *
 * @param T the cached value type
 * @param expiration cache entry TTL (default 30 min)
 * @param batchDelay delay before flushing a batch (default 50ms)
 */
class PlayerInfoCache<T>(
    private val expiration: Duration = 30.minutes,
    private val batchDelay: Duration = 50.milliseconds,
) {

    private data class Entry<T>(val value: T, val mark: TimeSource.Monotonic.ValueTimeMark)

    private val cache = mutableMapOf<String, Entry<T>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = LinkedHashMap<String, CompletableDeferred<T>>()
    private val mutex = Mutex()
    private var batchJob: Job? = null

    /** The function that fetches a batch of values by their IDs. Must be set before use. */
    var batchFetcher: (suspend (List<String>) -> Map<String, T>)? = null

    /** The factory for creating a default/fallback value when fetch returns nothing for an ID. */
    var defaultFactory: ((String) -> T)? = null

    private fun getFromCache(key: String): T? {
        val entry = cache[key] ?: return null
        return if (entry.mark.elapsedNow() < expiration) entry.value
        else {
            cache.remove(key)
            null
        }
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    operator fun minusAssign(key: String) = invalidate(key)

    suspend operator fun get(key: String): T {
        getFromCache(key)?.let { return it }
        val deferred = mutex.withLock {
            getFromCache(key)?.let { return it }
            pending[key] ?: CompletableDeferred<T>().also { pending[key] = it }
        }
        scheduleBatch()
        return deferred.await()
    }

    private fun scheduleBatch() {
        if (batchJob?.isActive == true) return
        batchJob = scope.launch {
            while (true) {
                delay(batchDelay)
                val batch = mutex.withLock {
                    if (pending.isEmpty()) return@withLock emptyMap()
                    val snapshot = pending.toMap()
                    pending.clear()
                    snapshot
                }
                if (batch.isEmpty()) break
                val ids = batch.keys.toList()
                val fetcher = batchFetcher
                    ?: throw IllegalStateException("PlayerInfoCache.batchFetcher not set")
                val defaults = defaultFactory
                    ?: throw IllegalStateException("PlayerInfoCache.defaultFactory not set")
                val fetched = runCatching { fetcher(ids) }.getOrElse { emptyMap() }
                val mark = TimeSource.Monotonic.markNow()
                ids.forEach { id ->
                    val value = fetched[id] ?: defaults(id)
                    cache[id] = Entry(value, mark)
                    batch[id]?.complete(value)
                }
                val hasMore = mutex.withLock { pending.isNotEmpty() }
                if (!hasMore) break
            }
        }
    }
}