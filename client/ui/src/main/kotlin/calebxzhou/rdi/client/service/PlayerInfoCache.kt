package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.database.PlayerInfoRecord
import calebxzhou.rdi.client.database.PlayerInfoStore
import calebxzhou.rdi.common.model.RAccount
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bson.types.ObjectId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val playerInfoCacheLogger = KotlinLogging.logger("PlayerInfoCache")

fun interface PlayerInfoSource {
    suspend fun fetch(ids: List<ObjectId>): Result<List<RAccount.Dto>>
}

class PlayerInfoCache(
    private val store: PlayerInfoStore?,
    private val source: PlayerInfoSource,
    private val defaultFactory: (ObjectId) -> RAccount.Dto,
    private val expiration: Duration = 30.minutes,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : AutoCloseable {
    private data class Entry(
        val value: RAccount.Dto,
        val updatedAt: Long
    )

    private data class Request(
        val deferred: CompletableDeferred<RAccount.Dto>,
        val version: Long
    )

    private data class FetchedInfo(
        val key: String,
        val request: Request,
        val info: RAccount.Dto,
        val updatedAt: Long
    )

    private val cache = ConcurrentHashMap<String, Entry>()
    private val pending = LinkedHashMap<String, Request>()
    private val inFlight = HashMap<String, Request>()
    private val versions = HashMap<String, Long>()
    private val mutex = Mutex()
    private val persistenceMutex = Mutex()
    private val wakeup = Channel<Unit>(Channel.CONFLATED)
    private val closed = AtomicBoolean()

    init {
        scope.launch { processBatches() }
    }

    fun peek(id: ObjectId): RAccount.Dto? = cache[id.toHexString()]
        ?.takeIf { isFresh(it.updatedAt) }
        ?.value

    suspend operator fun get(id: ObjectId): RAccount.Dto =
        getMany(listOf(id)).getOrThrow().getValue(id)

    suspend fun getMany(ids: Collection<ObjectId>): Result<Map<ObjectId, RAccount.Dto>> {
        if (ids.isEmpty()) return Result.success(emptyMap())
        return try {
            val uniqueIds = ids.distinct()
            val values = LinkedHashMap<ObjectId, RAccount.Dto>()
            val requests = mutableListOf<Pair<ObjectId, Request>>()
            var wakeWorker = false

            mutex.withLock {
                check(!closed.get()) { "PlayerInfoCache is closed" }
                uniqueIds.forEach { id ->
                    val key = id.toHexString()
                    val fresh = cache[key]?.takeIf { isFresh(it.updatedAt) }
                    if (fresh != null) {
                        values[id] = fresh.value
                        return@forEach
                    }

                    val request = pending[key] ?: inFlight[key] ?: run {
                        val version = versions.getOrPut(key) { 0L }
                        Request(
                            deferred = CompletableDeferred(scope.coroutineContext[kotlinx.coroutines.Job]),
                            version = version
                        ).also {
                            pending[key] = it
                            wakeWorker = true
                        }
                    }
                    requests += id to request
                }
                if (wakeWorker) {
                    wakeup.trySend(Unit)
                }
            }

            requests.forEach { (id, request) ->
                values[id] = request.deferred.await()
            }
            Result.success(values)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            Result.failure(cause)
        }
    }

    suspend fun prefetch(ids: Collection<ObjectId>): Result<Unit> =
        getMany(ids).map { }

    suspend fun put(info: RAccount.Dto): Result<Unit> {
        val key = info.id.toHexString()
        val updatedAt = clock()
        val request = mutex.withLock {
            versions[key] = (versions[key] ?: 0L) + 1L
            cache[key] = Entry(info, updatedAt)
            pending.remove(key) ?: inFlight.remove(key)
        }
        request?.deferred?.complete(info)

        val result = persistenceMutex.withLock {
            store?.upsertAll(listOf(info.toRecord(updatedAt))) ?: Result.success(Unit)
        }
        result.onFailure { cause ->
            playerInfoCacheLogger.warn(cause) { "保存玩家信息缓存失败：$key" }
        }
        return result
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        wakeup.close()
        scope.cancel()
        cache.clear()
    }

    private suspend fun processBatches() {
        try {
            for (ignored in wakeup) {
                val batch = mutex.withLock {
                    if (pending.isEmpty()) {
                        null
                    } else {
                        pending.toMap().also {
                            pending.clear()
                            inFlight.putAll(it)
                        }
                    }
                } ?: continue
                try {
                    processBatch(batch)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    playerInfoCacheLogger.error(cause) { "处理玩家信息缓存batch失败" }
                    failBatch(batch)
                }
            }
        } catch (cause: CancellationException) {
            throw cause
        }
    }

    private suspend fun failBatch(batch: Map<String, Request>) {
        for ((key, request) in batch) {
            val stale = cache[key]
            complete(
                key = key,
                request = request,
                value = stale?.value ?: defaultFactory(ObjectId(key)),
                updatedAt = stale?.updatedAt
            )
        }
    }

    private suspend fun processBatch(batch: Map<String, Request>) {
        val ids = batch.keys.toList()
        val persisted = store?.findByIds(ids)?.getOrElse { cause ->
            playerInfoCacheLogger.warn(cause) { "读取玩家信息缓存失败" }
            emptyMap()
        } ?: emptyMap()

        val unresolved = LinkedHashMap<String, Request>()
        for ((key, record) in persisted) {
            val request = batch[key] ?: continue
            if (isFresh(record.updatedAt)) {
                complete(
                    key = key,
                    request = request,
                    value = record.toDto(),
                    updatedAt = record.updatedAt
                )
            } else if (isActive(key, request)) {
                unresolved[key] = request
            }
        }
        for ((key, request) in batch) {
            if (!persisted.containsKey(key) && isActive(key, request)) {
                unresolved[key] = request
            }
        }
        if (unresolved.isEmpty()) return

        val fetched = try {
            source.fetch(unresolved.keys.map { ObjectId(it) })
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            Result.failure(cause)
        }

        if (fetched.isSuccess) {
            val fetchedById = fetched.getOrThrow().associateBy { it.id.toHexString() }
            val fetchedInfos = mutableListOf<FetchedInfo>()
            for ((key, request) in unresolved) {
                val info = fetchedById[key]
                if (info == null) {
                    complete(
                        key = key,
                        request = request,
                        value = defaultFactory(ObjectId(key)),
                        updatedAt = clock()
                    )
                } else {
                    val updatedAt = clock()
                    if (isActive(key, request)) fetchedInfos += FetchedInfo(key, request, info, updatedAt)
                }
            }
            if (fetchedInfos.isNotEmpty()) {
                val activeFetchedInfos = mutableListOf<FetchedInfo>()
                val persistenceResult = persistenceMutex.withLock {
                    for (fetchedInfo in fetchedInfos) {
                        if (isActive(fetchedInfo.key, fetchedInfo.request)) {
                            activeFetchedInfos += fetchedInfo
                        }
                    }
                    store?.upsertAll(activeFetchedInfos.map { it.info.toRecord(it.updatedAt) })
                        ?: Result.success(Unit)
                }
                persistenceResult.onFailure { cause ->
                    playerInfoCacheLogger.warn(cause) { "写入玩家信息缓存失败" }
                }
                for (fetchedInfo in activeFetchedInfos) {
                    complete(
                        key = fetchedInfo.key,
                        request = fetchedInfo.request,
                        value = fetchedInfo.info,
                        updatedAt = fetchedInfo.updatedAt
                    )
                }
            }
        } else {
            val cause = fetched.exceptionOrNull() ?: IllegalStateException("玩家信息请求失败")
            playerInfoCacheLogger.warn(cause) { "请求玩家信息失败" }
            for ((key, request) in unresolved) {
                val stale = persisted[key]?.toDto() ?: cache[key]?.value
                if (stale != null) {
                    complete(
                        key = key,
                        request = request,
                        value = stale,
                        updatedAt = persisted[key]?.updatedAt ?: cache[key]?.updatedAt ?: 0L
                    )
                } else {
                    complete(
                        key = key,
                        request = request,
                        value = defaultFactory(ObjectId(key)),
                        updatedAt = null
                    )
                }
            }
        }
    }

    private suspend fun isActive(key: String, request: Request?): Boolean = mutex.withLock {
        request != null && inFlight[key] === request && (versions[key] ?: 0L) == request.version
    }

    private suspend fun complete(
        key: String,
        request: Request,
        value: RAccount.Dto,
        updatedAt: Long?,
    ): Boolean = mutex.withLock {
        if (inFlight[key] !== request || (versions[key] ?: 0L) != request.version) return@withLock false
        inFlight.remove(key)
        updatedAt?.let { cache[key] = Entry(value, it) }
        request.deferred.complete(value)
        true
    }

    private fun isFresh(updatedAt: Long): Boolean = clock() - updatedAt < expiration.inWholeMilliseconds

    private fun PlayerInfoRecord.toDto() = RAccount.Dto(
        id = ObjectId(playerId),
        name = name,
        cloth = RAccount.Cloth(
            isSlim = isSlim,
            skin = skinUrl,
            cape = capeUrl
        )
    )

    private fun RAccount.Dto.toRecord(updatedAt: Long) = PlayerInfoRecord(
        playerId = id.toHexString(),
        name = name,
        isSlim = cloth.isSlim,
        skinUrl = cloth.skin,
        capeUrl = cloth.cape,
        updatedAt = updatedAt
    )
}
