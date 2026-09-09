package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.Modpack
import calebxzhou.rdi.master.service.modpack.ModpackVersionService.getUploaderPolicy
import calebxzhou.rdi.master.service.modpack.ModpackVersionService.resolveUploader
import calebxzhou.rdi.master.service.modpack.ModpackVersionService.updateUploaderPolicy
import calebxzhou.rdi.master.service.ModpackService.requireAuthor
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModpackServiceUploaderManagementTest {
    private lateinit var collection: MongoCollection<Modpack>

    @BeforeTest
    fun setup() {
        collection = mockk(relaxed = true)
        ModpackService.testDbcl = collection
        mockkObject(PlayerService)
    }

    @AfterTest
    fun cleanup() {
        ModpackService.testDbcl = null
        unmockkObject(PlayerService)
    }

    @Test
    fun `get preserves policy order and leaves missing ids in policy`() = runTest {
        val author = ModpackServiceTestFixtures.account("author")
        val first = ModpackServiceTestFixtures.account("first")
        val second = ModpackServiceTestFixtures.account("second")
        val missing = ObjectId()
        val ids = listOf(missing, second._id, first._id)
        coEvery { PlayerService.getByIds(ids) } returns listOf(first, second)

        val policy = ModpackContext(author, ModpackServiceTestFixtures.modpack(author._id, allowUploaderIds = ids), null)
            .getUploaderPolicy()

        assertEquals(ids, policy.allowUploaderIds)
        assertEquals(listOf(second._id, first._id), policy.uploaders.map { it.id })
    }

    @Test
    fun `resolve accepts exact name and qq and rejects invalid invitations`() = runTest {
        val author = ModpackServiceTestFixtures.account("author")
        val named = ModpackServiceTestFixtures.account("ExactName").copy(qq = "123456")
        val listed = ModpackServiceTestFixtures.account("listed")
        coEvery { PlayerService.getByQQ("ExactName") } returns null
        coEvery { PlayerService.getByName("ExactName") } returns named
        coEvery { PlayerService.getByQQ("123456") } returns named
        coEvery { PlayerService.getByQQ("missing") } returns null
        coEvery { PlayerService.getByName("missing") } returns null
        coEvery { PlayerService.getByQQ("author") } returns null
        coEvery { PlayerService.getByName("author") } returns author
        coEvery { PlayerService.getByQQ("listed") } returns null
        coEvery { PlayerService.getByName("listed") } returns listed
        coEvery { PlayerService.getByQQ("68b314bbadaf52ddab96b5ed") } returns null
        coEvery { PlayerService.getByName("68b314bbadaf52ddab96b5ed") } returns null

        val context = ModpackContext(
            author,
            ModpackServiceTestFixtures.modpack(author._id, allowUploaderIds = listOf(listed._id)),
            null,
        )
        assertEquals(named._id, context.resolveUploader(Modpack.UploaderResolveDto(" ExactName ")).id)
        assertEquals(named._id, context.resolveUploader(Modpack.UploaderResolveDto("123456")).id)
        assertFailsWith<RequestError> { context.resolveUploader(Modpack.UploaderResolveDto(" ")) }
        assertFailsWith<RequestError> { context.resolveUploader(Modpack.UploaderResolveDto("missing")) }
        assertFailsWith<RequestError> {
            context.resolveUploader(Modpack.UploaderResolveDto("68b314bbadaf52ddab96b5ed"))
        }
        assertFailsWith<RequestError> { context.resolveUploader(Modpack.UploaderResolveDto("author")) }
        assertFailsWith<RequestError> { context.resolveUploader(Modpack.UploaderResolveDto("listed")) }
    }

    @Test
    fun `normal listed uploader cannot manage policy`() {
        val author = ModpackServiceTestFixtures.account("author")
        val listed = ModpackServiceTestFixtures.account("listed")
        assertFailsWith<RequestError> {
            ModpackContext(
                listed,
                ModpackServiceTestFixtures.modpack(author._id, allowUploaderIds = listOf(listed._id)),
                null,
            ).requireAuthor()
        }
    }

    @Test
    fun `put validates policy and accepts null empty and ordered ids`() = runTest {
        val author = ModpackServiceTestFixtures.account("author")
        val first = ModpackServiceTestFixtures.account("first")
        val second = ModpackServiceTestFixtures.account("second")
        coEvery { PlayerService.getByIds(listOf(second._id, first._id)) } returns listOf(first, second)
        val update = slot<Bson>()
        val updateResult = mockk<UpdateResult>()
        every { updateResult.matchedCount } returns 1L
        coEvery { collection.updateOne(any<Bson>(), capture(update), any()) } returns updateResult
        val context = ModpackContext(author, ModpackServiceTestFixtures.modpack(author._id), null)

        context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(null))
        context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(emptyList()))
        context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(listOf(second._id, first._id)))
        assertTrue(update.captured.toString().contains(second._id.toHexString()))
        assertTrue(update.captured.toString().contains(first._id.toHexString()))

        assertFailsWith<RequestError> {
            context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(listOf(first._id, first._id)))
        }
        assertFailsWith<RequestError> {
            context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(listOf(author._id)))
        }
        coEvery { PlayerService.getByIds(listOf(ObjectId())) } returns emptyList()
        assertFailsWith<RequestError> {
            context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(listOf(ObjectId())))
        }
        assertFailsWith<RequestError> {
            context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(List(101) { ObjectId() }))
        }
    }

    @Test
    fun `put treats matched but unmodified as success and rejects missing pack`() = runTest {
        val author = ModpackServiceTestFixtures.account("author")
        val updateResult = mockk<UpdateResult>()
        every { updateResult.matchedCount } returns 1L
        every { updateResult.modifiedCount } returns 0L
        coEvery { collection.updateOne(any<Bson>(), any<Bson>(), any()) } returns updateResult
        val context = ModpackContext(author, ModpackServiceTestFixtures.modpack(author._id), null)
        context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(emptyList()))

        every { updateResult.matchedCount } returns 0L
        assertFailsWith<RequestError> {
            context.updateUploaderPolicy(Modpack.UploaderPolicyUpdateDto(null))
        }
    }
}
