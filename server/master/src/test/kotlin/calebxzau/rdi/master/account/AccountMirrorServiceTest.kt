package calebxzau.rdi.master.account

import calebxzau.rdi.server.account.AccountMirrorService
import calebxzau.rdi.server.account.InsertIfMissingResult
import calebxzau.rdi.server.account.AccountRecord
import calebxzau.rdi.server.account.PgAccountRepo
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.util.toUUID
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.bson.types.ObjectId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountMirrorServiceTest {
    private val account = RAccount(
        _id = ObjectId("00112233445566778899aabb"),
        name = "tester",
        pwd = "123456",
        qq = "12345",
        msid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        cloth = RAccount.Cloth(
            isSlim = false,
            skin = "https://example.com/skin.png",
            cape = "null"
        )
    )

    @Test
    fun `record mapping preserves account fields and maps literal null cape`() {
        val record = AccountRecord.from(account)

        assertEquals(account._id.toUUID(), record.id)
        assertEquals(account.name, record.name)
        assertEquals(account.pwd, record.pwd)
        assertEquals(account.qq, record.qq)
        assertEquals(account.msid, record.msid)
        assertEquals(false, record.isSlim)
        assertEquals(account.cloth.skin, record.skin)
        assertEquals(null, record.cape)
    }

    @Test
    fun `insert and upsert return Result instead of throwing database failures`() = runTest {
        val repository = mockk<PgAccountRepo>()
        val insertDatabase = mockk<DatabaseProvider>()
        coEvery {
            insertDatabase.transaction<InsertIfMissingResult>(any())
        } returns InsertIfMissingResult.INSERTED
        val insertService = AccountMirrorService(insertDatabase, repository)
        assertEquals(
            InsertIfMissingResult.INSERTED,
            insertService.insertIfMissing(account).getOrThrow()
        )

        val failureDatabase = mockk<DatabaseProvider>()
        coEvery {
            failureDatabase.transaction<Unit>(any())
        } throws IllegalStateException("database unavailable")
        val upsertResult = AccountMirrorService(failureDatabase, repository).upsert(account)
        assertTrue(upsertResult.isFailure)
        assertFalse(upsertResult.isSuccess)
    }

    @Test
    fun `scan counts outcomes and continues after one row failure`() = runTest {
        val database = mockk<DatabaseProvider>()
        val repository = mockk<PgAccountRepo>()
        val service = AccountMirrorService(database, repository)
        coEvery {
            database.transaction<InsertIfMissingResult>(any())
        } returnsMany listOf(
            InsertIfMissingResult.INSERTED,
            InsertIfMissingResult.CONFLICT("name", UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")),
            InsertIfMissingResult.EXISTING
        ) andThenThrows IllegalStateException("database unavailable")

        val summary = service.scan(flowOf(account, account, account, account)).getOrThrow()

        assertEquals(1, summary.inserted)
        assertEquals(1, summary.existing)
        assertEquals(1, summary.conflicts)
        assertEquals(1, summary.failed)
        coVerify(exactly = 4) { database.transaction<InsertIfMissingResult>(any()) }
    }

    @Test
    fun `conflict carries field and conflicting account id without account values`() {
        val conflictingId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
        val conflict = InsertIfMissingResult.CONFLICT("msid", conflictingId)

        assertEquals("msid", conflict.field)
        assertEquals(conflictingId, conflict.conflictingAccountId)
    }

    @Test
    fun `migration declares exactly the account contract`() {
        val sql = requireNotNull(javaClass.classLoader.getResource("db/migration/V1__create_account_schema.sql"))
            .readText()
        val columns = sql
            .substringAfter("CREATE TABLE account (")
            .substringBefore(");")
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("CONSTRAINT") }
            .map { it.removeSuffix(",").substringBefore(' ') }
            .toSet()

        assertEquals(
            setOf("id", "name", "pwd", "qq", "msid", "is_slim", "skin", "cape"),
            columns
        )
        assertTrue(sql.contains("id UUID PRIMARY KEY"))
        assertTrue(sql.contains("name TEXT NOT NULL UNIQUE"))
        assertTrue(sql.contains("pwd TEXT NOT NULL"))
        assertTrue(sql.contains("char_length(pwd) BETWEEN 6 AND 16"))
        assertTrue(sql.contains("qq TEXT NOT NULL UNIQUE"))
        assertTrue(sql.contains("qq ~ '^[0-9]{5,10}$'"))
        assertTrue(sql.contains("msid UUID UNIQUE"))
        assertTrue(sql.contains("is_slim BOOLEAN NOT NULL DEFAULT TRUE"))
        assertTrue(sql.contains("skin TEXT NOT NULL DEFAULT 'https://littleskin.cn/textures/526fe866ed25a7ee1cf894b81a2199aaa03f139803623a25a793f6ae57e22f02'"))
        assertTrue(sql.contains("cape TEXT"))
        assertFalse(sql.contains("REFERENCES"))
        assertFalse(sql.contains("FOREIGN KEY"))
    }
}
