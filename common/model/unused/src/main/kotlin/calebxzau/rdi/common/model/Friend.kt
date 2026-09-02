package calebxzau.rdi.common.model

import calebxzhou.rdi.common.UUIDSerializer
import calebxzhou.rdi.common.model.RAccount
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.util.UUID

@Serializable
enum class FriendRequestStatus { Pending, Accepted, Rejected, Expired }

@Serializable
data class Friend(
    @Contextual val id: ObjectId,
    val name: String,
    val cloth: RAccount.Cloth,
    val tags: List<FriendTag> = emptyList(),
)

@Serializable
data class FriendLookupDto(val qq: String)

@Serializable
data class FriendRequestDto(val qq: String)

@Serializable
data class FriendTagCreateDto(val name: String)

@Serializable
data class FriendTagRenameDto(val name: String)

@Serializable
data class FriendTagReplaceDto(val tagIds: List<String>)

@Serializable
data class FriendTag(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val name: String,
)
