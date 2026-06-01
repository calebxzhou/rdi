package calebxzhou.rdi.common.model.world

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class FirmSection(
    @Contextual
    val id: ObjectId = ObjectId(),
    val dimension: String,
    val chunkPos: Int,
    val sectionY: Byte,
)
