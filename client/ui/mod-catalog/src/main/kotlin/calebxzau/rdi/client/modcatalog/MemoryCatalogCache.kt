package calebxzau.rdi.client.modcatalog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class MemoryCatalogCache<K : Any, V : Any>(
    private val clock: Clock,
    private val positiveTtl: Duration,
    private val negativeTtl: Duration
) {
    private val mutex = Mutex()
    private val values = mutableMapOf<K, Entry<V>>()
    private val inFlight = mutableMapOf<K, CompletableDeferred<V?>>()

    suspend fun get(key: K): CachedCatalogValue<V>? = mutex.withLock {
        val now = clock.instant()
        values[key]?.takeIf { now.isBefore(it.expiresAt) }
            ?.let { CachedCatalogValue(it.value) }
            ?: run {
                values.remove(key)
                null
            }
    }

    suspend fun put(key: K, value: V?) {
        mutex.withLock {
            values[key] = Entry(
                value = value,
                expiresAt = clock.instant().plus(if (value == null) negativeTtl else positiveTtl)
            )
        }
    }

    suspend fun getOrLoad(key: K, load: suspend () -> V?): V? {
        val now = clock.instant()
        var owner = false
        val pending = mutex.withLock {
            values[key]?.takeIf { now.isBefore(it.expiresAt) }?.let { return it.value }
            values.remove(key)
            inFlight[key] ?: CompletableDeferred<V?>().also {
                inFlight[key] = it
                owner = true
            }
        }
        if (!owner) return pending.await()

        return try {
            val loaded = load()
            mutex.withLock {
                values[key] = Entry(
                    value = loaded,
                    expiresAt = clock.instant().plus(if (loaded == null) negativeTtl else positiveTtl)
                )
                inFlight.remove(key)
            }
            pending.complete(loaded)
            loaded
        } catch (cause: Throwable) {
            mutex.withLock { inFlight.remove(key) }
            pending.completeExceptionally(cause)
            throw cause
        }
    }

    private data class Entry<V>(
        val value: V?,
        val expiresAt: Instant
    )
}

internal data class CachedCatalogValue<V>(val value: V?)
