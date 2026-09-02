package calebxzau.rdi.server.friend

import calebxzau.rdi.common.model.FriendRequestStatus
import java.util.UUID

data class FriendRequestRecord(
    val id: UUID,
    val requesterId: UUID,
    val receiverId: UUID,
    val status: FriendRequestStatus,
    val createdAt: Long,
    val expiresAt: Long,
    val handledAt: Long?,
)

internal fun FriendRequestRecord.isExpired(now: Long): Boolean =
    status == FriendRequestStatus.Pending && expiresAt <= now

data class FriendshipRecord(
    val playerLowId: UUID,
    val playerHighId: UUID,
    val createdAt: Long,
    val removedAt: Long?,
)

data class FriendTagRecord(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val createdAt: Long,
)

data class FriendTagAssignmentRecord(
    val tagId: UUID,
    val friendId: UUID,
)

fun normalizedFriendPair(first: UUID, second: UUID): Pair<UUID, UUID> =
    if (comparePostgresUuid(first, second) < 0) first to second else second to first

/** PostgreSQL orders UUIDs as unsigned bytes, unlike Java's signed UUID comparison. */
private fun comparePostgresUuid(first: UUID, second: UUID): Int {
    val most = java.lang.Long.compareUnsigned(first.mostSignificantBits, second.mostSignificantBits)
    if (most != 0) return most
    return java.lang.Long.compareUnsigned(first.leastSignificantBits, second.leastSignificantBits)
}
