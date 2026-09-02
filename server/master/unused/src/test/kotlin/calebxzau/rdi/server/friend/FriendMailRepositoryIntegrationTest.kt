package calebxzau.rdi.server.friend

import calebxzau.rdi.common.model.FriendRequestStatus
import calebxzhou.rdi.common.model.MailKind
import calebxzhou.rdi.master.PostgresConfig
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import calebxzau.rdi.server.account.AccountRecord
import calebxzau.rdi.server.account.InsertIfMissingResult
import calebxzau.rdi.server.account.PgAccountRepo
import calebxzau.rdi.server.mail.MailRecord
import calebxzau.rdi.server.mail.PgMailRepo
import kotlinx.coroutines.test.runTest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class FriendMailRepositoryIntegrationTest {
    private lateinit var database: DatabaseProvider
    private val accounts = PgAccountRepo()
    private val friends = PgFriendRepo()
    private val mail = PgMailRepo()

    @BeforeTest
    fun setUp() {
        database = DatabaseProvider(
            PostgresConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maximumPoolSize = 4,
            )
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `pending request pair is unique regardless of request direction`() = runTest {
        val first = createAccount()
        val second = createAccount()

        val request = database.transaction { friends.createRequest(first, second) }
        val reverseFailure = runCatching {
            database.transaction { friends.createRequest(second, first) }
        }.exceptionOrNull()

        assertNotNull(reverseFailure)
        assertEquals(
            request.id,
            database.transaction { friends.findPendingRequest(second, first)?.id },
        )
    }

    @Test
    fun `accepted request uses compare and set and canonical friendship row`() = runTest {
        val requester = createAccount()
        val receiver = createAccount()
        val request = database.transaction { friends.createRequest(requester, receiver) }
        val handledAt = request.createdAt + 1

        assertTrue(
            database.transaction {
                friends.updateRequestStatus(
                    request.id,
                    FriendRequestStatus.Pending,
                    FriendRequestStatus.Accepted,
                    handledAt,
                )
            }
        )
        assertFalse(
            database.transaction {
                friends.updateRequestStatus(
                    request.id,
                    FriendRequestStatus.Pending,
                    FriendRequestStatus.Accepted,
                    handledAt + 1,
                )
            }
        )

        val friendship = database.transaction { friends.establishFriendship(receiver, requester, handledAt) }
        assertEquals(normalizedFriendPair(requester, receiver).first, friendship.playerLowId)
        assertEquals(normalizedFriendPair(requester, receiver).second, friendship.playerHighId)
        assertNull(friendship.removedAt)
        assertEquals(1, database.transaction { friends.listActiveFriendships(requester).size })
        assertEquals(FriendRequestStatus.Accepted, database.transaction { friends.findRequest(request.id)?.status })
    }

    @Test
    fun `tag listings only expose assignments through their owner`() = runTest {
        val owner = createAccount()
        val otherOwner = createAccount()
        val friend = createAccount()
        database.transaction {
            friends.establishFriendship(owner, friend, 1000)
            friends.establishFriendship(otherOwner, friend, 1000)
        }

        val ownerTag = database.transaction { friends.createTag(owner, "主队") }
        val otherTag = database.transaction { friends.createTag(otherOwner, "副队") }
        assertTrue(database.transaction { friends.assignTag(owner, ownerTag.id, friend) })
        assertTrue(database.transaction { friends.assignTag(otherOwner, otherTag.id, friend) })
        assertFalse(database.transaction { friends.assignTag(owner, otherTag.id, friend) })

        assertEquals(
            listOf(ownerTag.id),
            database.transaction { friends.listTagsForFriends(owner, listOf(friend))[friend].orEmpty().map { it.id } },
        )
        assertEquals(
            listOf(otherTag.id),
            database.transaction { friends.listTagsForFriends(otherOwner, listOf(friend))[friend].orEmpty().map { it.id } },
        )
    }

    @Test
    fun `mail inbox isolates receivers and sorts by created time`() = runTest {
        val sender = createAccount()
        val receiver = createAccount()
        val otherReceiver = createAccount()
        val older = MailRecord(
            id = UUID.randomUUID(),
            senderId = sender,
            receiverId = receiver,
            title = "older",
            content = "older",
            kind = MailKind.Normal,
            referenceId = null,
            unread = true,
            createdAt = 1_000,
        )
        val newer = older.copy(id = UUID.randomUUID(), title = "newer", content = "newer", createdAt = 2_000)
        val other = older.copy(id = UUID.randomUUID(), receiverId = otherReceiver, createdAt = 3_000)
        database.transaction {
            mail.insert(older)
            mail.insert(newer)
            mail.insert(other)
        }

        assertEquals(
            listOf(newer.id, older.id),
            database.transaction { mail.listInbox(receiver, createdAfter = 0).map { it.id } },
        )
        assertEquals(
            listOf(newer.id),
            database.transaction { mail.listInbox(receiver, createdAfter = 1_500).map { it.id } },
        )
        assertEquals(
            listOf(other.id),
            database.transaction { mail.listInbox(otherReceiver).map { it.id } },
        )
    }

    @Test
    fun `handled friend request is removed once its mail reference is gone`() = runTest {
        val requester = createAccount()
        val receiver = createAccount()
        val request = database.transaction { friends.createRequest(requester, receiver) }
        val requestMail = database.transaction {
            mail.create(
                senderId = requester,
                receiverId = receiver,
                title = "好友申请",
                content = "请处理",
                kind = MailKind.FriendRequest,
                referenceId = request.id,
            )
        }
        assertTrue(
            database.transaction {
                friends.updateRequestStatus(
                    request.id,
                    FriendRequestStatus.Pending,
                    FriendRequestStatus.Rejected,
                    request.createdAt + 1,
                )
            }
        )
        assertEquals(setOf(request.id), database.transaction { mail.friendRequestReferences() })

        database.transaction {
            friends.deleteHandledBefore(Long.MAX_VALUE, mail.friendRequestReferences())
        }
        assertNotNull(database.transaction { friends.findRequest(request.id) })
        assertTrue(database.transaction { mail.delete(requestMail.id, receiver) })
        assertTrue(database.transaction { mail.friendRequestReferences().isEmpty() })
        database.transaction {
            friends.deleteHandledBefore(Long.MAX_VALUE, mail.friendRequestReferences())
        }
        assertNull(database.transaction { friends.findRequest(request.id) })
    }

    private suspend fun createAccount(): UUID {
        val id = UUID.randomUUID()
        val result = database.transaction {
            accounts.insertIfMissing(
                AccountRecord(
                    id = id,
                    name = "friend-integration-${id.toString().take(8)}",
                    pwd = "123456",
                    qq = friendAccountSequence.getAndIncrement().toString(),
                    msid = null,
                    isSlim = true,
                    skin = "https://example.com/skin.png",
                    cape = null,
                )
            )
        }
        assertEquals(InsertIfMissingResult.INSERTED, result)
        return id
    }

    companion object {
        private val friendAccountSequence = AtomicInteger(30_000)

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18")
            .withDatabaseName("rdi")
            .withUsername("rdi")
            .withPassword("rdi")
    }
}
