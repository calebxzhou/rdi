package calebxzau.rdi.common.model

import calebxzhou.rdi.common.util.humanFileSize
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * calebxzau @ 2026-09-06 12:36
 */

@Serializable
data class BaseWorld(
    @Contextual
    val id: UUID,
    @Contextual
    val ownerId: UUID,
    val name: String,
    val levelType: String,
    val generatorSettings: String?,
    val size: Long,
) {
    companion object{
        const val MaxSize = 2*1024*1024*1024L
    }
    @Serializable
    data class CreateDto(
        val name: String,
        val levelType: String,
        val generatorSettings: String? = null,
        val size: Long,
    )

    @Serializable
    data class NameUpdateDto(
        val name: String,
    )
}

@Serializable
data class BaseWorldUploadSessionCreateDto(
    val size: Long,
    val sha1: String,
)

@Serializable
data class BaseWorldUploadSessionVo(
    @Contextual val id: UUID,
    val size: Long,
    val partSize: Int,
    val partCount: Int,
    val uploadedParts: List<Int>,
    val expiresAt: Long,
    val status: BaseWorldUploadStatus = BaseWorldUploadStatus.Uploading,
    val errorMessage: String? = null,
)

@Serializable
enum class BaseWorldUploadStatus {
    Uploading,
    Queued,
    Processing,
    Ready,
    Failed,
}
