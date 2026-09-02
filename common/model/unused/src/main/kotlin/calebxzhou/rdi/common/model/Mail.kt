package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.UUID
import calebxzau.rdi.common.model.FriendRequestStatus

@Serializable
enum class MailKind { Normal, System, FriendRequest }

@Serializable
enum class MailAction { Accept, Reject }

/** Mail IDs are PostgreSQL UUIDv7 values; public account IDs remain ObjectIds. */
@Serializable
data class Mail(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Contextual
    val senderId: ObjectId?,
    @Contextual
    val receiverId: ObjectId,
    val title: String,
    val content: String,
    val kind: MailKind = MailKind.Normal,
    @Serializable(with = UUIDSerializer::class)
    val referenceId: UUID? = null,
    val requestStatus: FriendRequestStatus? = null,
    val unread: Boolean = true,
    val createdAt: Long,
    val actions: List<MailAction> = emptyList(),
) {
    @Serializable
    data class Dto(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        @Contextual
        val senderId: ObjectId?,
        val senderName: String,
        val title: String,
        val content: String,
        val unread: Boolean,
        val kind: MailKind = MailKind.Normal,
        @Serializable(with = UUIDSerializer::class)
        val referenceId: UUID? = null,
        val requestStatus: FriendRequestStatus? = null,
        val createdAt: Long = 0,
        val actions: List<MailAction> = emptyList(),
    )

    @Serializable
    data class Vo(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val senderName: String,
        val title: String,
        val intro: String,
        val unread: Boolean,
        val kind: MailKind = MailKind.Normal,
        val requestStatus: FriendRequestStatus? = null,
        val createdAt: Long = 0,
        val actions: List<MailAction> = emptyList(),
    )
}
