package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Receipt(
    @Contextual
    val _id: ObjectId = ObjectId(),
    val requester: String,
    val msg: String,
) {
}
