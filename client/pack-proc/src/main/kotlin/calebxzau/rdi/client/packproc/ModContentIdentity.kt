package calebxzau.rdi.client.packproc

import calebxzhou.rdi.common.model.Mod
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.util.sha1
import calebxzhou.rdi.common.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compares mod records only when their recorded content identity is usable.
 *
 * CurseForge stores its unsigned 32-bit Murmur2 fingerprint as a decimal
 * value, while Modrinth stores SHA-1. GitHub records may use either SHA-1 or
 * SHA-256. Hashes from different algorithms are never compared directly.
 */
suspend fun sameModContent(
    first: Mod,
    firstFile: File?,
    second: Mod,
    secondFile: File?,
): Boolean = withContext(Dispatchers.IO) {
    val firstDigest = first.contentDigest() ?: return@withContext false
    val secondDigest = second.contentDigest() ?: return@withContext false

    if (firstDigest.algorithm == secondDigest.algorithm) {
        return@withContext firstDigest.value == secondDigest.value
    }

    // A CurseForge fingerprint and a SHA digest are not comparable. If both
    // local files are present, validate each against its own metadata first,
    // then compare their SHA-1 values as the common representation.
    if (firstFile == null || secondFile == null) return@withContext false
    if (!firstFile.matchesContentDigest(firstDigest) ||
        !secondFile.matchesContentDigest(secondDigest)
    ) {
        return@withContext false
    }
    firstFile.sha1.equals(secondFile.sha1, ignoreCase = true)
}

/** Returns whether [file] validates against the expected digest in [mod]. */
suspend fun fileMatchesModContent(file: File?, mod: Mod): Boolean = withContext(Dispatchers.IO) {
    val digest = mod.contentDigest() ?: return@withContext false
    file?.matchesContentDigest(digest) ?: false
}

private enum class ContentDigestAlgorithm {
    MURMUR2,
    SHA1,
    SHA256,
}

private data class ContentDigest(
    val algorithm: ContentDigestAlgorithm,
    val value: String,
)

private fun Mod.contentDigest(): ContentDigest? {
    val normalizedHash = hash.trim().lowercase()
    if (normalizedHash.isBlank()) return null
    return when (platform.trim().lowercase()) {
        "cf" -> normalizedHash.toULongOrNull()
            ?.takeIf { it != 0uL }
            ?.takeIf { it <= UInt.MAX_VALUE.toULong() }
            ?.let { ContentDigest(ContentDigestAlgorithm.MURMUR2, it.toString()) }
        "mr" -> normalizedHash
            .takeIf { it.matches(SHA1_PATTERN) }
            ?.let { ContentDigest(ContentDigestAlgorithm.SHA1, it) }
        "github" -> when {
            normalizedHash.matches(SHA1_PATTERN) ->
                ContentDigest(ContentDigestAlgorithm.SHA1, normalizedHash)
            normalizedHash.matches(SHA256_PATTERN) ->
                ContentDigest(ContentDigestAlgorithm.SHA256, normalizedHash)
            else -> null
        }
        else -> null
    }
}

private fun File.matchesContentDigest(digest: ContentDigest): Boolean {
    if (!exists() || !isFile) return false
    return when (digest.algorithm) {
        ContentDigestAlgorithm.MURMUR2 -> murmur2.toULong().toString() == digest.value
        ContentDigestAlgorithm.SHA1 -> sha1.equals(digest.value, ignoreCase = true)
        ContentDigestAlgorithm.SHA256 -> sha256.equals(digest.value, ignoreCase = true)
    }
}

private val SHA1_PATTERN = Regex("[0-9a-f]{40}")
private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
