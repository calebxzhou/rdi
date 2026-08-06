package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.database.PlayerInfoRecord
import calebxzhou.rdi.client.database.PlayerInfoStore
import calebxzhou.rdi.common.model.RAccount
import kotlinx.coroutines.runBlocking
import org.bson.types.ObjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class PlayerInfoCacheTest {
    private val id = ObjectId("68b314bbadaf52ddab96b5ed")
    private val info = RAccount.Dto(
        id = id,
        name = "玩家一",
        cloth = RAccount.Cloth(
            isSlim = false,
            skin = "https://example.com/skin",
            cape = "https://example.com/cape"
        )
    )

    @Test
    fun `persisted info is loaded before server`() = runBlocking {
        val store = MemoryPlayerInfoStore().apply { upsertAll(listOf(info.toRecord(10L))) }
        var fetchCount = 0
        val cache = PlayerInfoCache(
            store = store,
            source = PlayerInfoSource {
                fetchCount++
                Result.success(listOf(info.copy(name = "server")))
            },
            defaultFactory = { RAccount.DEFAULT.dto },
            clock = { 20L }
        )

        assertEquals(info, cache[id])
        assertEquals(0, fetchCount)
        assertEquals(info, cache.peek(id))
        cache.close()
    }

    @Test
    fun `server result is persisted and survives a new cache`() = runBlocking {
        val store = MemoryPlayerInfoStore()
        var fetchCount = 0
        val first = PlayerInfoCache(
            store = store,
            source = PlayerInfoSource {
                fetchCount++
                Result.success(listOf(info))
            },
            defaultFactory = { RAccount.DEFAULT.dto },
            clock = { 100L }
        )

        assertEquals(info, first[id])
        first.close()

        val second = PlayerInfoCache(
            store = store,
            source = PlayerInfoSource {
                fetchCount++
                Result.success(emptyList())
            },
            defaultFactory = { RAccount.DEFAULT.dto },
            clock = { 110L }
        )
        assertEquals(info, second[id])
        assertEquals(1, fetchCount)
        second.close()
    }

    @Test
    fun `server failure returns stale persisted info`() = runBlocking {
        val store = MemoryPlayerInfoStore().apply { upsertAll(listOf(info.toRecord(10L))) }
        val cache = PlayerInfoCache(
            store = store,
            source = PlayerInfoSource { Result.failure(IllegalStateException("offline")) },
            defaultFactory = { RAccount.DEFAULT.dto },
            expiration = 10.milliseconds,
            clock = { 100L }
        )

        assertEquals(info, cache[id])
        assertNull(cache.peek(id))
        cache.close()
    }

    @Test
    fun `getMany batches requests and persists all results`() = runBlocking {
        val store = MemoryPlayerInfoStore()
        val secondId = ObjectId("68c901f07c76a32fa7dc270a")
        val ids = listOf(id, secondId)
        var fetchCount = 0
        val cache = PlayerInfoCache(
            store = store,
            source = PlayerInfoSource { ids ->
                fetchCount++
                Result.success(ids.map { id -> info.copy(id = id) })
            },
            defaultFactory = { RAccount.DEFAULT.dto }
        )

        val result = cache.getMany(ids).getOrThrow()

        assertEquals(ids.map { it to info.copy(id = it) }.toMap(), result)
        assertEquals(1, fetchCount)
        assertEquals(2, store.findByIds(ids.map(ObjectId::toHexString)).getOrThrow().size)
        cache.close()
    }

    private class MemoryPlayerInfoStore : PlayerInfoStore {
        private val records = linkedMapOf<String, PlayerInfoRecord>()

        override suspend fun findByIds(ids: Collection<String>): Result<Map<String, PlayerInfoRecord>> =
            Result.success(ids.mapNotNull { records[it] }.associateBy { it.playerId })

        override suspend fun upsertAll(records: Collection<PlayerInfoRecord>): Result<Unit> {
            records.forEach { this.records[it.playerId] = it }
            return Result.success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            records.remove(id)
            return Result.success(Unit)
        }
    }

    private fun RAccount.Dto.toRecord(updatedAt: Long) = PlayerInfoRecord(
        playerId = id.toHexString(),
        name = name,
        isSlim = cloth.isSlim,
        skinUrl = cloth.skin,
        capeUrl = cloth.cape,
        updatedAt = updatedAt
    )
}
