package calebxzhou.rdi.client.service

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.common.net.LocalArtifactHashAlgorithm
import calebxzhou.rdi.common.net.LocalArtifactRequest
import calebxzhou.rdi.common.net.LocalArtifactReuser
import calebxzhou.rdi.common.service.murmur2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

data class LocalArtifactSources(
    val runtimeRoots: List<Path> = emptyList(),
    val searchFiles: List<Path> = emptyList()
)

@Serializable
data class CachedLocalFileHash(
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val sha1: String? = null,
    val murmur2: Long? = null
)

class LocalArtifactFileReuser(
    private val sourceProvider: () -> LocalArtifactSources,
    cachedHashes: List<CachedLocalFileHash> = emptyList()
) : LocalArtifactReuser {
    private val hashCache = ConcurrentHashMap(cachedHashes.associateBy(CachedLocalFileHash::path))

    override suspend fun reuse(request: LocalArtifactRequest, target: Path): Result<Path?> = withContext(Dispatchers.IO) {
        runCatching {
            if (matches(target, request)) return@runCatching target
            val sources = sourceProvider()
            val relativeCandidates = request.relativePaths.flatMap { relativePath ->
                val relative = Path.of(relativePath).normalize()
                if (relative.isAbsolute || relative.startsWith("..")) emptyList()
                else sources.runtimeRoots.map { it.resolve(relative) }
            }
            val candidates = (relativeCandidates + sources.searchFiles)
                .distinctBy { it.toAbsolutePath().normalize() }
                .filterNot { sameFile(it, target) }
                .filter { candidate ->
                    runCatching {
                        Files.isRegularFile(candidate, NOFOLLOW_LINKS) &&
                            (request.size == null || Files.size(candidate) == request.size)
                    }.onFailure {
                        lgr.warn(it) { "读取本地文件失败，将跳过该来源：$candidate" }
                    }.getOrDefault(false)
                }
            candidates.firstOrNull { candidate ->
                runCatching {
                    val matching = matches(candidate, request)
                    if (matching) copyVerified(candidate, target, request)
                    matching
                }
                    .onFailure { lgr.warn(it) { "本地文件复制失败，将尝试其他来源：$candidate" } }
                    .getOrDefault(false)
            }
        }
    }

    fun cachedHashes(): List<CachedLocalFileHash> = hashCache.values.toList()

    private fun matches(path: Path, request: LocalArtifactRequest): Boolean {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) return false
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        if (request.size != null && attributes.size() != request.size) return false
        val key = path.toAbsolutePath().normalize().toString()
        val cached = hashCache[key]
            ?.takeIf { it.size == attributes.size() && it.modifiedAt == attributes.lastModifiedTime().toMillis() }
            ?: CachedLocalFileHash(key, attributes.size(), attributes.lastModifiedTime().toMillis())
        val updated = when (request.algorithm) {
            LocalArtifactHashAlgorithm.SHA1 -> cached.sha1?.let { cached }
                ?: cached.copy(sha1 = path.sha1.lowercase())

            LocalArtifactHashAlgorithm.CURSEFORGE_MURMUR2 -> cached.murmur2?.let { cached }
                ?: cached.copy(murmur2 = path.murmur2)
        }
        hashCache[key] = updated
        return when (request.algorithm) {
            LocalArtifactHashAlgorithm.SHA1 -> updated.sha1.equals(request.hash.trim(), ignoreCase = true)
            LocalArtifactHashAlgorithm.CURSEFORGE_MURMUR2 -> updated.murmur2 == request.hash.trim().toLongOrNull()
        }
    }

    private fun copyVerified(source: Path, target: Path, request: LocalArtifactRequest) {
        target.parent?.let { Files.createDirectories(it) }
        val temporary = target.resolveSibling("${target.fileName}.local-copying")
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            check(matches(temporary, request)) { "复制后的文件校验失败：$source" }
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            hashCache.remove(temporary.toAbsolutePath().normalize().toString())
            Files.deleteIfExists(temporary)
        }
    }

    private fun sameFile(left: Path, right: Path): Boolean =
        Files.exists(left) && Files.exists(right) && runCatching { Files.isSameFile(left, right) }.getOrDefault(false)

    private companion object {
        val lgr by Loggers
    }
}
