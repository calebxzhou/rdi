package calebxzau.rdi.server.friend

import calebxzau.rdi.common.model.FriendRequestStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Synchronous Exposed operations; callers own DatabaseProvider.transaction. */
class PgFriendRepo {
    fun createRequest(requesterId: UUID, receiverId: UUID): FriendRequestRecord {
        require(requesterId != receiverId) { "a player cannot add themselves" }
        return FriendRequestTable.insertReturning {
            it[FriendRequestTable.requesterId] = requesterId
            it[FriendRequestTable.receiverId] = receiverId
        }.single().toFriendRequestRecord()
    }

    fun findRequest(id: UUID): FriendRequestRecord? = FriendRequestTable.selectAll()
        .where { FriendRequestTable.id eq id }
        .singleOrNull()
        ?.toFriendRequestRecord()

    fun findRequests(ids: Collection<UUID>): Map<UUID, FriendRequestRecord> {
        if (ids.isEmpty()) return emptyMap()
        return FriendRequestTable.selectAll()
            .where { FriendRequestTable.id inList ids.toList() }
            .map { it.toFriendRequestRecord() }
            .associateBy { it.id }
    }

    fun findRequestForUpdate(id: UUID): FriendRequestRecord? = FriendRequestTable.selectAll()
        .where { FriendRequestTable.id eq id }
        .forUpdate()
        .singleOrNull()
        ?.toFriendRequestRecord()

    fun findPendingRequest(first: UUID, second: UUID): FriendRequestRecord? {
        require(first != second) { "a player cannot add themselves" }
        return FriendRequestTable.selectAll()
            .where {
                (FriendRequestTable.status eq FriendRequestStatus.Pending.name) and
                    (((FriendRequestTable.requesterId eq first) and (FriendRequestTable.receiverId eq second)) or
                        ((FriendRequestTable.requesterId eq second) and (FriendRequestTable.receiverId eq first)))
            }
            .singleOrNull()
            ?.toFriendRequestRecord()
    }

    fun findLatestRequest(first: UUID, second: UUID, status: FriendRequestStatus? = null): FriendRequestRecord? {
        require(first != second) { "a player cannot add themselves" }
        return FriendRequestTable.selectAll()
            .where {
                (((FriendRequestTable.requesterId eq first) and (FriendRequestTable.receiverId eq second)) or
                    ((FriendRequestTable.requesterId eq second) and (FriendRequestTable.receiverId eq first))) and
                    (status?.let { FriendRequestTable.status eq it.name } ?: (FriendRequestTable.id eq FriendRequestTable.id))
            }
            .orderBy(FriendRequestTable.createdAt, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toFriendRequestRecord()
    }

    fun countActiveFriendships(playerId: UUID): Long = FriendshipTable.selectAll()
        .where {
            ((FriendshipTable.playerLowId eq playerId) or (FriendshipTable.playerHighId eq playerId)) and
                (FriendshipTable.removedAt eq null)
        }
        .count()

    fun countRequestsSince(requesterId: UUID, since: Long): Long = FriendRequestTable.selectAll()
        .where {
            (FriendRequestTable.requesterId eq requesterId) and
                (FriendRequestTable.createdAt greaterEq since)
        }
        .count()

    /** Compare-and-set status transition. */
    fun updateRequestStatus(
        id: UUID,
        expected: FriendRequestStatus,
        status: FriendRequestStatus,
        handledAt: Long,
    ): Boolean = FriendRequestTable.update({
        (FriendRequestTable.id eq id) and (FriendRequestTable.status eq expected.name)
    }) {
        it[FriendRequestTable.status] = status.name
        it[FriendRequestTable.handledAt] = handledAt
    } == 1

    fun expireRequests(now: Long): Int = FriendRequestTable.update({
        (FriendRequestTable.status eq FriendRequestStatus.Pending.name) and
            (FriendRequestTable.expiresAt lessEq now)
    }) {
        it[FriendRequestTable.status] = FriendRequestStatus.Expired.name
        it[FriendRequestTable.handledAt] = now
    }

    /** Removes completed request history after mail retention, preserving referenced requests. */
    fun deleteHandledBefore(before: Long, retainedReferences: Set<UUID>): Int {
        val candidates = FriendRequestTable.selectAll()
            .where { FriendRequestTable.handledAt less before }
            .map { it[FriendRequestTable.id] }
            .filterNot { it in retainedReferences }
        if (candidates.isEmpty()) return 0
        return FriendRequestTable.deleteWhere { FriendRequestTable.id inList candidates }
    }

    fun findFriendship(first: UUID, second: UUID): FriendshipRecord? {
        val (low, high) = normalizedFriendPair(first, second)
        return FriendshipTable.selectAll()
            .where {
                (FriendshipTable.playerLowId eq low) and (FriendshipTable.playerHighId eq high)
            }
            .singleOrNull()
            ?.toFriendshipRecord()
    }

    fun listActiveFriendships(playerId: UUID): List<FriendshipRecord> = FriendshipTable.selectAll()
        .where {
            ((FriendshipTable.playerLowId eq playerId) or (FriendshipTable.playerHighId eq playerId)) and
                (FriendshipTable.removedAt eq null)
        }
        .orderBy(FriendshipTable.createdAt, SortOrder.DESC)
        .map { it.toFriendshipRecord() }

    /** Creates or reactivates the one canonical row for a pair. */
    fun establishFriendship(first: UUID, second: UUID, now: Long): FriendshipRecord {
        val (low, high) = normalizedFriendPair(first, second)
        FriendshipTable.insertIgnore {
            it[FriendshipTable.playerLowId] = low
            it[FriendshipTable.playerHighId] = high
        }
        FriendshipTable.update({
            (FriendshipTable.playerLowId eq low) and (FriendshipTable.playerHighId eq high)
        }) {
            it[FriendshipTable.createdAt] = now
            it[FriendshipTable.removedAt] = null
        }
        return findFriendship(low, high) ?: error("friendship was not created")
    }

    fun removeFriendship(first: UUID, second: UUID, removedAt: Long): Boolean {
        val (low, high) = normalizedFriendPair(first, second)
        return FriendshipTable.update({
            (FriendshipTable.playerLowId eq low) and
                (FriendshipTable.playerHighId eq high) and
                (FriendshipTable.removedAt eq null)
        }) {
            it[FriendshipTable.removedAt] = removedAt
        } == 1
    }

    fun createTag(ownerId: UUID, name: String): FriendTagRecord = FriendTagTable.insertReturning {
        it[FriendTagTable.ownerId] = ownerId
        it[FriendTagTable.name] = name
    }.single().toFriendTagRecord()

    fun findTag(id: UUID): FriendTagRecord? = FriendTagTable.selectAll()
        .where { FriendTagTable.id eq id }
        .singleOrNull()
        ?.toFriendTagRecord()

    fun listTags(ownerId: UUID): List<FriendTagRecord> = FriendTagTable.selectAll()
        .where { FriendTagTable.ownerId eq ownerId }
        .orderBy(FriendTagTable.name, SortOrder.ASC)
        .map { it.toFriendTagRecord() }

    fun renameTag(id: UUID, ownerId: UUID, name: String): Boolean = FriendTagTable.update({
        (FriendTagTable.id eq id) and (FriendTagTable.ownerId eq ownerId)
    }) {
        it[FriendTagTable.name] = name
    } == 1

    fun deleteTag(id: UUID, ownerId: UUID): Boolean = FriendTagTable.deleteWhere {
        (FriendTagTable.id eq id) and (FriendTagTable.ownerId eq ownerId)
    } == 1

    fun assignTag(ownerId: UUID, tagId: UUID, friendId: UUID): Boolean {
        if (!tagBelongsTo(tagId, ownerId)) return false
        return FriendTagAssignmentTable.insertIgnore {
            it[FriendTagAssignmentTable.tagId] = tagId
            it[FriendTagAssignmentTable.friendId] = friendId
        }.insertedCount == 1
    }

    fun listTagsForFriends(ownerId: UUID, friendIds: Collection<UUID>): Map<UUID, List<FriendTagRecord>> {
        if (friendIds.isEmpty()) return emptyMap()
        val ownedTags = FriendTagTable.selectAll()
            .where { FriendTagTable.ownerId eq ownerId }
            .map { it[FriendTagTable.id] }
        if (ownedTags.isEmpty()) return emptyMap()
        val assignments = FriendTagAssignmentTable.selectAll()
            .where {
                (FriendTagAssignmentTable.friendId inList friendIds.toList()) and
                    (FriendTagAssignmentTable.tagId inList ownedTags)
            }
            .map { it.toFriendTagAssignmentRecord() }
        if (assignments.isEmpty()) return emptyMap()
        val tagIds = assignments.map { it.tagId }.toSet()
        val tags = FriendTagTable.selectAll()
            .where { FriendTagTable.id inList tagIds }
            .map { it.toFriendTagRecord() }
            .associateBy { it.id }
        return assignments.groupBy(FriendTagAssignmentRecord::friendId).mapValues { (_, rows) ->
            rows.mapNotNull { tags[it.tagId] }
        }
    }

    /** Removes the current player's private labels when a friendship ends. */
    fun deleteAssignmentsForFriend(ownerId: UUID, friendId: UUID): Int {
        val ownedTagIds = FriendTagTable.selectAll()
            .where { FriendTagTable.ownerId eq ownerId }
            .map { it[FriendTagTable.id] }
        if (ownedTagIds.isEmpty()) return 0
        return FriendTagAssignmentTable.deleteWhere {
            (FriendTagAssignmentTable.friendId eq friendId) and
                (FriendTagAssignmentTable.tagId inList ownedTagIds)
        }
    }

    private fun tagBelongsTo(tagId: UUID, ownerId: UUID): Boolean = FriendTagTable.selectAll()
        .where { (FriendTagTable.id eq tagId) and (FriendTagTable.ownerId eq ownerId) }
        .limit(1)
        .any()
}

internal object FriendRequestTable : Table("friend_request") {
    val id = javaUUID("id").databaseGenerated()
    val requesterId = javaUUID("requester_id")
    val receiverId = javaUUID("receiver_id")
    val status = text("status")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val handledAt = long("handled_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object FriendshipTable : Table("friendship") {
    val playerLowId = javaUUID("player_low_id")
    val playerHighId = javaUUID("player_high_id")
    val createdAt = long("created_at")
    val removedAt = long("removed_at").nullable()
    override val primaryKey = PrimaryKey(playerLowId, playerHighId)
}

internal object FriendTagTable : Table("friend_tag") {
    val id = javaUUID("id").databaseGenerated()
    val ownerId = javaUUID("owner_id")
    val name = text("name")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object FriendTagAssignmentTable : Table("friend_tag_assignment") {
    val tagId = javaUUID("tag_id")
    val friendId = javaUUID("friend_id")
    override val primaryKey = PrimaryKey(tagId, friendId)
}

private fun ResultRow.toFriendRequestRecord() = FriendRequestRecord(
    id = this[FriendRequestTable.id], requesterId = this[FriendRequestTable.requesterId],
    receiverId = this[FriendRequestTable.receiverId],
    status = FriendRequestStatus.valueOf(this[FriendRequestTable.status]),
    createdAt = this[FriendRequestTable.createdAt], expiresAt = this[FriendRequestTable.expiresAt],
    handledAt = this[FriendRequestTable.handledAt]
)

private fun ResultRow.toFriendshipRecord() = FriendshipRecord(
    playerLowId = this[FriendshipTable.playerLowId], playerHighId = this[FriendshipTable.playerHighId],
    createdAt = this[FriendshipTable.createdAt], removedAt = this[FriendshipTable.removedAt]
)

private fun ResultRow.toFriendTagRecord() = FriendTagRecord(
    id = this[FriendTagTable.id], ownerId = this[FriendTagTable.ownerId],
    name = this[FriendTagTable.name], createdAt = this[FriendTagTable.createdAt]
)

private fun ResultRow.toFriendTagAssignmentRecord() = FriendTagAssignmentRecord(
    tagId = this[FriendTagAssignmentTable.tagId], friendId = this[FriendTagAssignmentTable.friendId]
)
