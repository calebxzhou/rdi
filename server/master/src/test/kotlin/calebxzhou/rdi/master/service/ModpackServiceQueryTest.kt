package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.Modpack
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.DistinctFlow
import com.mongodb.kotlin.client.coroutine.FindFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.FlowCollector
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.mockk.slot
import kotlin.test.assertTrue
import calebxzhou.rdi.master.service.ModpackService.toBriefVo
import calebxzhou.rdi.master.service.ModpackService.toDetailVo
import org.bson.conversions.Bson
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.CountOptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.bson.types.ObjectId
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.service.modpack.ModpackQueryService

class ModpackServiceQueryTest {
    private lateinit var collection: MongoCollection<Modpack>

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        mockkObject(PlayerService)
    }

    @AfterTest
    fun cleanup() {
        unmockkObject(PlayerService)
        ModpackService.testDbcl = null
    }

    @Test
    fun `basic queries return mongo results and play count increments`() = runTest {
        val owner = ModpackServiceTestFixtures.account("owner")
        val pack = ModpackServiceTestFixtures.modpack(owner._id)
        stubFind(listOf(pack))
        coEvery { collection.updateOne(any<Bson>(), any<Bson>()) } returns mockk(relaxed = true)

        assertEquals(listOf(pack), ModpackService.listByAuthor(owner._id))
        assertEquals(pack, ModpackService.getById(pack._id))
        assertEquals(listOf(pack), ModpackService.searchByName("Pack"))
        ModpackService.incrementPlayCount(pack._id)
        coVerify(exactly = 1) { collection.updateOne(any<Bson>(), any<Bson>(), any<UpdateOptions>()) }
    }

    @Test
    fun `blank name search is short circuited`() = runTest {
        assertEquals(emptyList(), ModpackService.searchByName("  "))
        coVerify(exactly = 0) { collection.find(any<Bson>()).toList() }
    }

    @Test
    fun `brief and detail mapping includes latest version and fallback author`() = runTest {
        val authorId = org.bson.types.ObjectId()
        val pack = ModpackServiceTestFixtures.modpack(authorId, "Mapped", versions = mutableListOf())
        pack.versions += ModpackServiceTestFixtures.version(
            pack,
            mods = mutableListOf(ModpackServiceTestFixtures.mod("one"))
        )
        coEvery { PlayerService.getName(authorId) } returns null

        val brief = pack.toBriefVo()
        val detail = pack.toDetailVo()
        assertEquals("未知作者", brief.authorName)
        assertEquals(1, brief.modCount)
        assertEquals(123L, brief.fileSize)
        assertEquals("未知作者", detail.authorName)
        assertEquals(1, detail.versions.size)
        assertNull(detail.sourceUrl)
    }

    @Test
    fun `list by ids returns empty without querying for empty input`() = runTest {
        assertEquals(emptyList(), ModpackService.listByIds(emptyList()))
        coVerify(exactly = 0) { collection.find(any<Bson>()).toList() }
    }

    @Test
    fun `find missing ids preserves order and removes duplicates`() = runTest {
        val foundId = ObjectId()
        val missingId = ObjectId()
        val distinctFlow = mockk<DistinctFlow<ObjectId>>()
        val fieldName = slot<String>()
        val filter = slot<Bson>()
        every { collection.distinct<ObjectId>(capture(fieldName), capture(filter)) } returns distinctFlow
        coEvery { distinctFlow.collect(any()) } coAnswers {
            firstArg<FlowCollector<ObjectId>>().emit(foundId)
        }

        assertEquals(
            listOf(missingId),
            ModpackQueryService.findMissingIds(listOf(foundId, missingId, foundId))
        )
        assertEquals("_id", fieldName.captured)
        val filterText = filter.captured.toString()
        assertTrue(filterText.contains("_id"))
        assertTrue(filterText.contains("\$in"))
        assertTrue(filterText.contains(foundId.toString()))
        assertTrue(filterText.contains(missingId.toString()))
        verify(exactly = 1) { collection.distinct<ObjectId>(any(), any<Bson>()) }
    }

    @Test
    fun `find missing ids returns empty and avoids database for empty input`() = runTest {
        assertEquals(emptyList(), ModpackQueryService.findMissingIds(emptyList()))
        verify(exactly = 0) { collection.distinct<ObjectId>(any(), any<Bson>()) }
    }

    @Test
    fun `find missing ids accepts exactly 512 entries`() = runTest {
        val ids = List(512) { ObjectId() }
        val distinctFlow = mockk<DistinctFlow<ObjectId>>()
        every { collection.distinct<ObjectId>(any(), any<Bson>()) } returns distinctFlow
        coEvery { distinctFlow.collect(any()) } coAnswers { }

        assertEquals(ids, ModpackQueryService.findMissingIds(ids))
        verify(exactly = 1) { collection.distinct<ObjectId>(any(), any<Bson>()) }
    }

    @Test
    fun `find missing ids rejects more than 512 before database access`() = runTest {
        val id = ObjectId()
        val ids = List(513) { id }

        assertFailsWith<ParamError> { ModpackQueryService.findMissingIds(ids) }
        verify(exactly = 0) { collection.distinct<ObjectId>(any(), any<Bson>()) }
    }

    @Test
    fun `search parses filters pagination and reports count`() = runTest {
        val call = mockk<ApplicationCall>(relaxed = true)
        every { call.parameters } returns Parameters.build {
            append("q", "magic")
            append("category", "TECH")
            append("mcVer", "1.21.1")
            append("sort", "POPULAR")
            append("limit", "2")
            append("offset", "3")
        }
        val flow = mockk<FindFlow<Modpack>>()
        val filter = slot<Bson>()
        every { collection.find(capture(filter)) } returns flow
        every { flow.sort(any()) } returns flow
        every { flow.skip(any()) } returns flow
        every { flow.limit(any()) } returns flow
        coEvery { flow.collect(any()) } coAnswers { }
        coEvery { collection.countDocuments(any<Bson>(), any<CountOptions>()) } returns 5L

        val result = ModpackService.search(call)
        assertEquals(5, result.total)
        assertEquals(3, result.offset)
        assertEquals(2, result.limit)
        assertTrue(result.items.isEmpty())
        assertTrue(filter.captured.toString().contains("TECH"))
        assertTrue(filter.captured.toString().contains("V211"))
    }

    @Test
    fun `search rejects invalid category before database access`() = runTest {
        val call = mockk<ApplicationCall>(relaxed = true)
        every { call.parameters } returns Parameters.build { append("category", "NOPE") }
        assertFailsWith<Exception> { ModpackService.search(call) }
        coVerify(exactly = 0) { collection.countDocuments(any<Bson>()) }
    }

    @Test
    fun `list all and simple use empty results without author lookups`() = runTest {
        val flow = mockk<FindFlow<Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        every { flow.sort(any()) } returns flow
        coEvery { flow.collect(any()) } coAnswers { }
        assertTrue(ModpackService.listAll().isEmpty())
        assertTrue(ModpackService.listSimple(hasIconOnly = true).isEmpty())
    }

    private fun stubFind(items: List<Modpack>) {
        val flow = mockk<FindFlow<Modpack>>()
        every { collection.find(any<Bson>()) } returns flow
        coEvery { flow.collect(any()) } coAnswers {
            val collector = firstArg<FlowCollector<Modpack>>()
            items.forEach { collector.emit(it) }
        }
    }
}
