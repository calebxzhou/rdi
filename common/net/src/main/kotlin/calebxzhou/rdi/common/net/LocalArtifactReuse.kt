package calebxzhou.rdi.common.net

import java.nio.file.Path

enum class LocalArtifactHashAlgorithm {
    SHA1,
    SHA256,
    CURSEFORGE_MURMUR2
}

data class LocalArtifactRequest(
    val algorithm: LocalArtifactHashAlgorithm,
    val hash: String,
    val size: Long? = null,
    val relativePaths: List<String> = emptyList()
)

fun interface LocalArtifactReuser {
    suspend fun reuse(request: LocalArtifactRequest, target: Path): Result<Path?>
}

object LocalArtifactReuse {
    @Volatile
    private var reuser = LocalArtifactReuser { _, _ -> Result.success(null) }

    fun install(value: LocalArtifactReuser) {
        reuser = value
    }

    suspend fun reuse(request: LocalArtifactRequest, target: Path): Result<Path?> =
        reuser.reuse(request, target)
}
