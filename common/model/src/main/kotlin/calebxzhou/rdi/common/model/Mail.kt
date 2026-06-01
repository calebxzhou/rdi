package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Mail(
    @Contextual
    val _id: ObjectId = ObjectId(),
    @Contextual
    val senderId: ObjectId ,
    @Contextual
    val receiverId: ObjectId,
    val title: String,
    val content: String,
    val unread: Boolean = true,
) {
    @Serializable
    data class Dto(
        @Contextual
        val id: ObjectId,
        @Contextual
        val senderId: ObjectId,
        val senderName: String,
        val title: String,
        val content: String,
        val unread: Boolean,
    )

    fun toDto(senderName: String) = Dto(
        id = _id,
        senderId = senderId,
        senderName = senderName,
        title = title,
        content = content,
        unread = unread,
    )
    @Serializable
    data class Vo(
        @Contextual
        val id: ObjectId,
        val senderName: String,
        val title: String,
        val intro: String,
        val unread: Boolean,
    )
}