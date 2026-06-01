package calebxzhou.rdi.common.model

import calebxzhou.rdi.common.UNKNOWN_PLAYER_ID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ChatMsg(
    @Contextual val _id: ObjectId = ObjectId(),
    @Contextual
    val uid: ObjectId = UNKNOWN_PLAYER_ID,
    val content: String,
    val global: Boolean = true,
){
    constructor(sender: RAccount, content: String, global: Boolean = true): this(ObjectId(), sender._id, content, global)
    fun toDto(sender: RAccount) = Dto(sender._id,sender.name,content)
    @Serializable
    data class Dto(
        @Contextual
        val senderId: ObjectId,
        val senderName: String,
        val content: String,
    )
}
