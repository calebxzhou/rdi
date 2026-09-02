package calebxzau.rdi.server.friend

import calebxzau.rdi.common.model.FriendRequestStatus
import calebxzhou.rdi.master.service.IN_APP_MAIL_RETENTION_MILLIS
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FriendPersistenceContractTest {
    @Test
    fun `canonical pair is independent of request direction`() {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

        assertEquals(normalizedFriendPair(first, second), normalizedFriendPair(second, first))
        assertEquals(first to second, normalizedFriendPair(first, second))
    }

    @Test
    fun `canonical pair follows postgres unsigned uuid ordering`() {
        val postgresLow = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff")
        val postgresHigh = UUID.fromString("80000000-0000-0000-0000-000000000000")

        assertEquals(postgresLow to postgresHigh, normalizedFriendPair(postgresHigh, postgresLow))
    }

    @Test
    fun `migration gives requests three day expiry and terminal states`() {
        val sql = requireNotNull(javaClass.classLoader.getResource("db/migration/V7__create_mail_friend_schema.sql"))
            .readText()

        assertTrue(sql.contains("259200000"), "friend requests must expire after three days")
        FriendRequestStatus.entries.forEach { status ->
            assertTrue(sql.contains("'$status'"), "migration must support $status requests")
        }
    }

    @Test
    fun `pending request is expired before an action can be accepted`() {
        val request = FriendRequestRecord(
            id = UUID.randomUUID(),
            requesterId = UUID.randomUUID(),
            receiverId = UUID.randomUUID(),
            status = FriendRequestStatus.Pending,
            createdAt = 0,
            expiresAt = 100,
            handledAt = null,
        )

        assertTrue(request.isExpired(100))
        assertTrue(request.isExpired(101))
        assertTrue(!request.isExpired(99))
    }

    @Test
    fun `migration indexes mail retention timestamp`() {
        val sql = requireNotNull(javaClass.classLoader.getResource("db/migration/V7__create_mail_friend_schema.sql"))
            .readText()

        assertTrue(sql.contains("created_at BIGINT"))
        assertTrue(sql.contains("mail_expiry_idx"))
        assertEquals(30L * 24 * 60 * 60 * 1000, IN_APP_MAIL_RETENTION_MILLIS)
    }
}
