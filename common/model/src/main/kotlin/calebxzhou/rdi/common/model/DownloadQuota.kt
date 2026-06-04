package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class DownloadQuota(
    val _id: @Contextual ObjectId = ObjectId(),
    val uid: @Contextual ObjectId,
    val day: String,
    val usedBytes: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    @Serializable
    data class Vo(
        val limitBytes: Long,
        val usedBytes: Long,
        val remainingBytes: Long,
        val day: String
    )
}
