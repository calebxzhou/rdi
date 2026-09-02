package calebxzhou.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ModpackUploadSessionCreateDto(
    val fileName: String,
    val size: Long,
    val sha1: String
)

@Serializable
data class ModpackUploadSessionVo(
    @Contextual val id: UUID,
    val fileName: String,
    val size: Long,
    val sha1: String,
    val partSize: Int,
    val partCount: Int,
    val uploadedParts: List<Int>,
    val ready: Boolean,
    val expiresAt: Long,
    val maxParallelParts: Int = 8
)

@Serializable
data class ModpackCreateFromUploadDto(
    @Contextual val uploadId: UUID,
    val modpack: Modpack.CreateWithVersionDto
)

@Serializable
data class ModpackVersionCreateFromUploadDto(
    @Contextual val uploadId: UUID,
    val mods: MutableList<Mod>
)
