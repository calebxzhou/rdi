package calebxzhou.rdi.client.service.content

import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Progress
import calebxzau.rdi.common.logging.Loggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

private val lgr by Loggers

enum class LegacyContentPlatform {
    CURSEFORGE,
    MODRINTH,
    GITHUB,
    RDI_CORE,
}

data class ParsedLegacyContentName(
    val platform: LegacyContentPlatform,
    val embeddedHash: String? = null,
)

data class ClientContentMigrationSummary(
    val migrated: Int,
    val skipped: Int,
    val totalItems: Int,
    val completedBytes: Long,
    val totalBytes: Long,
) {
    val message: String
        get() = "已整理${migrated}个，跳过${skipped}个"
}

data class ClientContentMigrationAvailability(
    val hasMigratableFiles: Boolean,
    val hasMigratableModFiles: Boolean,
)

/**
 * Moves direct old content entries into the digest cache. This class is
 * intentionally the only client production reader of the historical
 * dl-mods/dl-packs directories.
 */
class ClientContentMigrator(
    private val sourceRoot: Path,
    private val destinationRoot: Path,
    private val packSourceRoot: Path,
    private val contentStore: ClientContentStore = ClientContentStore(destinationRoot),
) {
    fun migrationAvailability(): ClientContentMigrationAvailability =
        listSourceFiles().let { files ->
            ClientContentMigrationAvailability(
                hasMigratableFiles = files.isNotEmpty(),
                hasMigratableModFiles = files.any { it.kind == LegacyContentKind.MOD },
            )
        }

    internal fun hasMigratableFiles(): Boolean = migrationAvailability().hasMigratableFiles

    suspend fun migrate(context: Task2Context): ClientContentMigrationSummary = withContext(Dispatchers.IO) {
        val files = listSourceFiles()
        Files.createDirectories(destinationRoot)
        ensureDirectory(destinationRoot, "客户端内容缓存目录")

        val totalBytes = files.sumOf { runCatching { Files.size(it.path) }.getOrDefault(0L) }
        val completedItems = AtomicInteger(0)
        val migrated = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val completedBytes = AtomicLong(0L)
        val progressLock = Any()
        val startedAt = System.nanoTime()
        emitProgress(
            context = context,
            completedItems = 0,
            totalItems = files.size,
            completedBytes = 0L,
            totalBytes = totalBytes,
            bytesPerSecond = 0.0,
            fraction = if (files.isEmpty()) 1f else 0f,
        )

        coroutineScope {
            files.map { source ->
                async(Dispatchers.IO) {
                    context.ensureActive()
                    val sourceBytes = runCatching { Files.size(source.path) }.getOrDefault(0L)
                    val migratedOne = try {
                        migrateOne(source)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Throwable) {
                        lgr.warn(error) { "整理客户端旧内容失败，保留源文件: ${source.path.toAbsolutePath()}" }
                        false
                    }
                    synchronized(progressLock) {
                        if (migratedOne) migrated.incrementAndGet() else skipped.incrementAndGet()
                        val done = completedItems.incrementAndGet()
                        val bytes = completedBytes.addAndGet(sourceBytes)
                        val elapsed = max(1L, System.nanoTime() - startedAt) / 1_000_000_000.0
                        emitProgress(
                            context = context,
                            completedItems = done,
                            totalItems = files.size,
                            completedBytes = bytes,
                            totalBytes = totalBytes,
                            bytesPerSecond = bytes / elapsed,
                            fraction = done.toFloat().div(files.size.coerceAtLeast(1)),
                        )
                    }
                }
            }.awaitAll()
        }

        ClientContentMigrationSummary(
            migrated = migrated.get(),
            skipped = skipped.get(),
            totalItems = files.size,
            completedBytes = completedBytes.get(),
            totalBytes = totalBytes,
        )
    }

    private fun listSourceFiles(): List<MigrationSource> =
        listSourceFiles(
            root = sourceRoot,
            label = "客户端旧内容目录",
            kind = LegacyContentKind.MOD,
            predicate = { it.endsWith(".jar", ignoreCase = true) },
        ) + listSourceFiles(
            root = packSourceRoot,
            label = "客户端旧整合包目录",
            kind = LegacyContentKind.PACK,
            predicate = { it.endsWith(".tar.zst", ignoreCase = true) || it.endsWith(".zip", ignoreCase = true) },
        )

    private fun listSourceFiles(
        root: Path,
        label: String,
        kind: LegacyContentKind,
        predicate: (String) -> Boolean,
    ): List<MigrationSource> {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        ensureDirectory(root, label)
        return try {
            Files.newDirectoryStream(root).use { entries ->
                entries.asSequence()
                    .filter { path ->
                        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                            predicate(path.fileName.toString())
                    }
                    .map { path -> MigrationSource(path, kind) }
                    .sortedBy { it.path.fileName.toString().lowercase(Locale.ROOT) }
                    .toList()
            }
        } catch (error: Throwable) {
            throw IOException("无法读取${label}: $root", error)
        }
    }

    private suspend fun migrateOne(source: MigrationSource): Boolean {
        if (source.kind == LegacyContentKind.PACK) return migratePack(source.path)

        return migrateLegacyMod(source.path)
    }

    private suspend fun migratePack(source: Path): Boolean {
        val sha1 = contentStore.calculateDigestForMigration(source, ContentDigestAlgorithm.SHA1)
        return contentStore.migrateLegacySource(
            source = source,
            destinations = listOf(ContentDigest(ContentDigestAlgorithm.SHA1, sha1)),
        )
    }

    private suspend fun migrateLegacyMod(source: Path): Boolean {
        val parsed = parseLegacyContentName(source.fileName.toString())
        if (parsed == null) {
            lgr.warn { "跳过未知客户端旧内容文件名: ${source.fileName}" }
            return false
        }

        val plan = when (parsed.platform) {
            LegacyContentPlatform.CURSEFORGE -> {
                val expected = parsed.embeddedHash
                    ?: return invalidSource(source, "CurseForge文件名缺少摘要")
                val normalized = expected.toULongOrNull()
                    ?.takeIf { it <= UInt.MAX_VALUE.toULong() }
                    ?.toString()
                    ?: return invalidSource(source, "CurseForge文件名摘要无效")
                val actual = contentStore.calculateDigestForMigration(
                    source,
                    ContentDigestAlgorithm.MURMUR2,
                )
                if (actual != normalized) return invalidSource(source, "CurseForge文件摘要不匹配")
                MigrationPlan(
                    lockDigests = listOf(ContentDigest(ContentDigestAlgorithm.MURMUR2, normalized)),
                    destinations = listOf(
                        Destination(ContentDigestAlgorithm.MURMUR2, normalized),
                    ),
                )
            }

            LegacyContentPlatform.MODRINTH -> {
                val expected = parsed.embeddedHash
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf(::isSha1)
                    ?: return invalidSource(source, "Modrinth文件名摘要无效")
                val actual = contentStore.calculateDigestForMigration(source, ContentDigestAlgorithm.SHA1)
                if (actual != expected) return invalidSource(source, "Modrinth文件摘要不匹配")
                MigrationPlan(
                    lockDigests = listOf(ContentDigest(ContentDigestAlgorithm.SHA1, expected)),
                    destinations = listOf(Destination(ContentDigestAlgorithm.SHA1, expected)),
                )
            }

            LegacyContentPlatform.GITHUB -> {
                val expected = parsed.embeddedHash
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { isSha1(it) || isSha256(it) }
                    ?: return invalidSource(source, "GitHub文件名摘要无效")
                val actualSha256 = contentStore.calculateDigestForMigration(source, ContentDigestAlgorithm.SHA256)
                if (isSha256(expected) && actualSha256 != expected) {
                    return invalidSource(source, "GitHub文件SHA-256摘要不匹配")
                }
                val actualSha1 = contentStore.calculateDigestForMigration(source, ContentDigestAlgorithm.SHA1)
                if (isSha1(expected) && actualSha1 != expected) {
                    return invalidSource(source, "GitHub文件SHA-1摘要不匹配")
                }
                MigrationPlan(
                    lockDigests = listOf(
                        ContentDigest(ContentDigestAlgorithm.SHA1, actualSha1),
                        ContentDigest(ContentDigestAlgorithm.SHA256, actualSha256),
                    ),
                    destinations = listOf(
                        Destination(ContentDigestAlgorithm.SHA256, actualSha256),
                        Destination(ContentDigestAlgorithm.SHA1, actualSha1),
                    ),
                )
            }

            LegacyContentPlatform.RDI_CORE -> {
                val sha1 = contentStore.calculateDigestForMigration(source, ContentDigestAlgorithm.SHA1)
                MigrationPlan(
                    lockDigests = listOf(ContentDigest(ContentDigestAlgorithm.SHA1, sha1)),
                    destinations = listOf(Destination(ContentDigestAlgorithm.SHA1, sha1)),
                )
            }
        }

        val destinations = plan.destinations.map { destination ->
            ContentDigest(destination.algorithm, destination.value)
        }
        return contentStore.migrateLegacySource(
            source = source,
            destinations = destinations,
            lockDigests = plan.lockDigests,
        )
    }

    private fun invalidSource(source: Path, reason: String): Boolean {
        lgr.warn { "$reason，保留源文件: ${source.toAbsolutePath()}" }
        return false
    }

    private fun emitProgress(
        context: Task2Context,
        completedItems: Int,
        totalItems: Int,
        completedBytes: Long,
        totalBytes: Long,
        bytesPerSecond: Double,
        fraction: Float,
    ) {
        context.emit(
            Task2Progress(
                message = "整理中",
                fraction = fraction.coerceIn(0f, 1f),
                completedItems = completedItems,
                totalItems = totalItems,
                completedBytes = completedBytes,
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond.takeIf { it > 0.0 },
            )
        )
    }

    private fun ensureDirectory(path: Path, label: String) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("${label}不可访问: $path")
        }
    }

    private data class Destination(
        val algorithm: ContentDigestAlgorithm,
        val value: String,
    )

    private data class MigrationPlan(
        val lockDigests: List<ContentDigest>,
        val destinations: List<Destination>,
    )

    private enum class LegacyContentKind {
        MOD,
        PACK,
    }

    private data class MigrationSource(
        val path: Path,
        val kind: LegacyContentKind,
    )

    companion object {
        fun parseFileName(fileName: String): ParsedLegacyContentName? = parseLegacyContentName(fileName)
    }
}

private fun parseLegacyContentName(fileName: String): ParsedLegacyContentName? {
    val name = fileName.substringAfterLast('/', fileName.substringAfterLast('\\'))
    if (!name.endsWith(".jar", ignoreCase = true)) return null
    val stem = name.dropLast(4)
    if (stem.startsWith("rdi-5-mc-client-", ignoreCase = true) &&
        stem.length > "rdi-5-mc-client-".length
    ) {
        return ParsedLegacyContentName(LegacyContentPlatform.RDI_CORE)
    }

    val marker = listOf("cf", "mr", "github")
        .mapNotNull { platform ->
            val index = stem.lastIndexOf("_${platform}_", ignoreCase = true)
            if (index <= 0) null else index to platform
        }
        .maxByOrNull { it.first }
        ?: return null
    val hashStart = marker.first + marker.second.length + 2
    val hash = stem.substring(hashStart).takeIf(String::isNotBlank) ?: return null
    val platform = when (marker.second.lowercase(Locale.ROOT)) {
        "cf" -> LegacyContentPlatform.CURSEFORGE
        "mr" -> LegacyContentPlatform.MODRINTH
        "github" -> LegacyContentPlatform.GITHUB
        else -> return null
    }
    return ParsedLegacyContentName(platform, hash)
}

private fun isSha1(value: String): Boolean = value.matches(Regex("[0-9a-f]{40}"))

private fun isSha256(value: String): Boolean = value.matches(Regex("[0-9a-f]{64}"))

