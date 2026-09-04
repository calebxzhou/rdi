package calebxzhou.rdi.client.service.content

import calebxzhou.rdi.client.service.ClientDirs
import calebxzhou.rdi.client.service.ClientTaskManager
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.util.humanFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

data class DownloadCacheCleanupSummary(
    val deletedFiles: Int,
    val releasedBytes: Long,
    val retainedFiles: Int,
    val busyFiles: Int,
)

class DownloadCacheCleanupPartialFailureException(
    val summary: DownloadCacheCleanupSummary,
    val failedFiles: Int,
    failures: List<Throwable>,
) : IOException("清除下载缓存时有${failedFiles}个文件删除失败", failures.firstOrNull())

/** Removes only digest cache entries no longer represented by an installed Legacy pack. */
class DownloadCacheCleanupService(
    private val cacheRoot: Path = ClientDirs.dlcDir.toPath(),
    private val versionsRoot: Path = ClientDirs.versionsDir.toPath(),
    private val digestCalculator: suspend (Path, ContentDigestAlgorithm) -> String =
        { path, algorithm -> ClientContentStore.shared.calculateDigestForMigration(path, algorithm) },
    private val deleteCandidate: suspend (Path) -> Boolean = { path -> Files.deleteIfExists(path) },
) {
    private data class Candidate(
        val path: Path,
        val digest: ContentDigest,
        val size: Long,
    )

    private data class LegacyVersion(val directory: Path)

    suspend fun cleanup(context: Task2Context? = null): Result<DownloadCacheCleanupSummary> = try {
        withContext(Dispatchers.IO) { cleanupInternal(context) }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    suspend fun clear(): Result<DownloadCacheCleanupSummary> = cleanup()

    private suspend fun cleanupInternal(context: Task2Context?): Result<DownloadCacheCleanupSummary> {
        ensureActive(context)
        emitProgress(context, Task2Progress("正在读取下载缓存", 0f))
        val candidates = enumerateCandidates()
        emitProgress(
            context,
            Task2Progress(
                message = "已读取下载缓存",
                fraction = CACHE_ENUMERATION_FRACTION,
                completedItems = candidates.size,
                totalItems = candidates.size,
                completedBytes = 0L,
                totalBytes = candidates.sumOf { it.size },
            ),
        )
        val candidatesBySize = candidates.groupBy { it.size }
        val retained = mutableSetOf<ContentDigest>()
        val legacyVersions = enumerateLegacyVersions()

        emitProgress(
            context,
            Task2Progress(
                message = "正在扫描本地整合包",
                fraction = LOCAL_SCAN_START_FRACTION,
                completedItems = 0,
                totalItems = legacyVersions.size,
            ),
        )
        for ((index, legacyVersion) in legacyVersions.withIndex()) {
            ensureActive(context)
            scanContentRoots(legacyVersion.directory, candidatesBySize, retained)
            val completed = index + 1
            emitProgress(
                context,
                Task2Progress(
                    message = "正在扫描本地整合包：${legacyVersion.directory.fileName}",
                    fraction = LOCAL_SCAN_START_FRACTION +
                        LOCAL_SCAN_FRACTION * completed / legacyVersions.size.coerceAtLeast(1),
                    completedItems = completed,
                    totalItems = legacyVersions.size,
                ),
            )
        }

        emitProgress(
            context,
            Task2Progress(
                message = "正在删除未使用下载缓存",
                fraction = DELETION_START_FRACTION,
                completedItems = 0,
                totalItems = candidates.size,
                completedBytes = 0L,
                totalBytes = candidates.sumOf { it.size },
            ),
        )
        var deletedFiles = 0
        var releasedBytes = 0L
        var retainedFiles = 0
        var busyFiles = 0
        var failedFiles = 0
        val failures = mutableListOf<Throwable>()
        var completedCandidates = 0
        var completedBytes = 0L
        val totalBytes = candidates.sumOf { it.size }
        for (candidate in candidates) {
            ensureActive(context)
            if (candidate.digest in retained) {
                retainedFiles++
            } else {
                ClientContentStoreCoordinator.withDigestLock(candidate.digest) {
                    if (!isEligibleCandidate(candidate)) return@withDigestLock
                    if (ClientContentStoreCoordinator.isLeased(candidate.path)) {
                        busyFiles++
                        return@withDigestLock
                    }
                    try {
                        if (deleteCandidate(candidate.path)) {
                            deletedFiles++
                            releasedBytes += candidate.size
                        }
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (cause: Throwable) {
                        failedFiles++
                        failures += cause
                    }
                }
            }
            completedCandidates++
            completedBytes += candidate.size
            emitProgress(
                context,
                Task2Progress(
                    message = "正在删除未使用下载缓存：${candidate.path.fileName}",
                    fraction = DELETION_START_FRACTION +
                        DELETION_FRACTION * completedCandidates / candidates.size.coerceAtLeast(1),
                    completedItems = completedCandidates,
                    totalItems = candidates.size,
                    completedBytes = completedBytes,
                    totalBytes = totalBytes,
                ),
            )
        }
        val summary = DownloadCacheCleanupSummary(
            deletedFiles = deletedFiles,
            releasedBytes = releasedBytes,
            retainedFiles = retainedFiles,
            busyFiles = busyFiles,
        )
        return if (failedFiles > 0) {
            Result.failure(DownloadCacheCleanupPartialFailureException(summary, failedFiles, failures))
        } else {
            emitProgress(
                context,
                Task2Progress(
                    message = summaryMessage(summary),
                    fraction = 1f,
                    completedItems = candidates.size,
                    totalItems = candidates.size,
                    completedBytes = totalBytes,
                    totalBytes = totalBytes,
                ),
            )
            Result.success(summary)
        }
    }

    private fun enumerateCandidates(): List<Candidate> {
        if (!Files.isDirectory(cacheRoot, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        Files.list(cacheRoot).use { entries ->
            val result = mutableListOf<Candidate>()
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                val fileName = path.fileName.toString()
                val match = CACHE_NAME_PATTERN.matchEntire(fileName) ?: continue
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                val algorithm = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "sha1" -> ContentDigestAlgorithm.SHA1
                    "sha256" -> ContentDigestAlgorithm.SHA256
                    "murmur2" -> ContentDigestAlgorithm.MURMUR2
                    else -> continue
                }
                val value = match.groupValues[1]
                val validValue = when (algorithm) {
                    ContentDigestAlgorithm.SHA1 -> value.matches(Regex("[0-9a-fA-F]{40}"))
                    ContentDigestAlgorithm.SHA256 -> value.matches(Regex("[0-9a-fA-F]{64}"))
                    ContentDigestAlgorithm.MURMUR2 -> value.all(Char::isDigit)
                }
                if (!validValue) continue
                if (algorithm == ContentDigestAlgorithm.MURMUR2 &&
                    (value.toULongOrNull()?.let { it <= UInt.MAX_VALUE.toULong() } != true)
                ) continue
                result += Candidate(
                    path,
                    ContentDigest(algorithm, value.lowercase(Locale.ROOT)),
                    Files.size(path),
                )
            }
            return result
        }
    }

    private fun enumerateLegacyVersions(): List<LegacyVersion> {
        if (!Files.isDirectory(versionsRoot, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        Files.list(versionsRoot).use { entries ->
            val result = mutableListOf<LegacyVersion>()
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue
                val match = LEGACY_VERSION_PATTERN.matchEntire(path.fileName.toString())
                    ?: continue
                result += LegacyVersion(path)
            }
            return result
        }
    }

    private suspend fun scanContentRoots(
        versionDirectory: Path,
        candidatesBySize: Map<Long, List<Candidate>>,
        retained: MutableSet<ContentDigest>,
    ) {
        for (directoryName in CONTENT_DIRECTORIES) {
            currentCoroutineContext().ensureActive()
            val root = versionDirectory.resolve(directoryName)
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) continue
            Files.walk(root).use { paths ->
                val iterator = paths.iterator()
                while (iterator.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    val path = iterator.next()
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                    val size = Files.size(path)
                    val sameSize = candidatesBySize[size] ?: continue
                    val algorithms = sameSize.map { it.digest.algorithm }.toSet()
                    for (algorithm in algorithms) {
                        val digest = ContentDigest(algorithm, digestCalculator(path, algorithm))
                        if (sameSize.any { it.digest == digest }) retained += digest
                    }
                }
            }
        }
    }

    private fun isEligibleCandidate(candidate: Candidate): Boolean =
        candidate.path.parent?.toAbsolutePath()?.normalize() == cacheRoot.toAbsolutePath().normalize() &&
            Files.isRegularFile(candidate.path, LinkOption.NOFOLLOW_LINKS) &&
            candidate.path.fileName.toString().equals(
                "${candidate.digest.normalizedValue}.${candidate.digest.algorithm.suffix}",
                ignoreCase = true,
            )

    private companion object {
        const val CACHE_ENUMERATION_FRACTION = 0.1f
        const val LOCAL_SCAN_START_FRACTION = 0.1f
        const val LOCAL_SCAN_FRACTION = 0.4f
        const val DELETION_START_FRACTION = 0.5f
        const val DELETION_FRACTION = 0.5f
        val CACHE_NAME_PATTERN = Regex(
            "([0-9a-fA-F]{40}|[0-9a-fA-F]{64}|[0-9]+)\\.(sha1|sha256|murmur2)",
            RegexOption.IGNORE_CASE,
        )
        val LEGACY_VERSION_PATTERN = Regex("([0-9a-fA-F]{24})_(.+)")
        val CONTENT_DIRECTORIES = listOf("mods", "resourcepacks", "shaderpacks")
    }

    private suspend fun ensureActive(context: Task2Context?) {
        currentCoroutineContext().ensureActive()
        context?.ensureActive()
    }

    private suspend fun emitProgress(context: Task2Context?, progress: Task2Progress) {
        ensureActive(context)
        context?.emit(progress)
    }

    private fun summaryMessage(summary: DownloadCacheCleanupSummary): String = buildString {
        append("已清除${summary.deletedFiles}个下载缓存，释放${summary.releasedBytes.humanFileSize}")
        if (summary.busyFiles > 0) append("；${summary.busyFiles}个正在使用的缓存已保留")
    }
}

internal const val DOWNLOAD_CACHE_CLEANUP_DEDUPE_KEY = "client-download-cache-cleanup"

fun buildDownloadCacheCleanupTask2(
    service: DownloadCacheCleanupService = DownloadCacheCleanupService(),
): Task2 = Task2.Leaf("清除下载缓存") { context ->
    service.cleanup(context).getOrThrow()
}

fun submitDownloadCacheCleanupTask2(
    service: DownloadCacheCleanupService = DownloadCacheCleanupService(),
): String = ClientTaskManager.submit(
    task = buildDownloadCacheCleanupTask2(service),
    dedupeKey = DOWNLOAD_CACHE_CLEANUP_DEDUPE_KEY,
)
