package calebxzhou.rdi.client.service.content

import kotlinx.coroutines.runBlocking
import calebxzhou.rdi.common.service.murmur2
import calebxzhou.rdi.common.util.sha1 as sha1Digest
import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2Progress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadCacheCleanupServiceTest {
    @Test
    fun `deletes unused recognized entries and preserves matching local content`() = runBlocking {
        val root = Files.createTempDirectory("dlc-test")
        try {
            val cache = root.resolve("dlc").createDirectories()
            val versions = root.resolve("versions").createDirectories()
            val usedBytes = "used-content".toByteArray()
            val unusedBytes = "unused-content".toByteArray()
            val usedSha1 = sha1(usedBytes)
            val unusedSha1 = sha1(unusedBytes)
            val archiveSha1 = sha1("archive-only-client-pack".toByteArray())
            val usedPath = cache.resolve("$usedSha1.sha1").also { it.writeBytes(usedBytes) }
            val unusedPath = cache.resolve("$unusedSha1.sha1").also { it.writeBytes(unusedBytes) }
            val archivePath = cache.resolve("$archiveSha1.sha1").also { it.writeBytes("archive-only-client-pack".toByteArray()) }
            val unknownPath = cache.resolve("unknown.bin").also { it.writeBytes(unusedBytes) }
            val temporaryPath = cache.resolve(".tmp").also { it.createDirectories() }
            val local = versions.resolve("0123456789abcdef01234567_pack").resolve("mods").createDirectories()
                .resolve("manual.jar").also { it.writeBytes(usedBytes) }

            val result = DownloadCacheCleanupService(
                cacheRoot = cache,
                versionsRoot = versions,
                digestCalculator = ::calculateDigest,
            ).cleanup().getOrThrow()

            assertEquals(2, result.deletedFiles)
            assertEquals((unusedBytes.size + "archive-only-client-pack".length).toLong(), result.releasedBytes)
            assertTrue(usedPath.exists())
            assertFalse(archivePath.exists())
            assertFalse(unusedPath.exists())
            assertTrue(unknownPath.exists())
            assertTrue(temporaryPath.exists())
            assertTrue(local.exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preflight digest failure leaves cache unchanged`() = runBlocking {
        val root = Files.createTempDirectory("dlc-test")
        try {
            val cache = root.resolve("dlc").createDirectories()
            val versions = root.resolve("versions").createDirectories()
            val bytes = "unused-content".toByteArray()
            val path = cache.resolve("${sha1(bytes)}.sha1").also { it.writeBytes(bytes) }
            versions.resolve("0123456789abcdef01234567_pack").resolve("mods").createDirectories()
                .resolve("manual.jar").writeBytes(bytes)

            val result = DownloadCacheCleanupService(
                cacheRoot = cache,
                versionsRoot = versions,
                digestCalculator = { _, _ -> error("digest failed") },
            ).cleanup()

            assertTrue(result.isFailure)
            assertTrue(path.exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uppercase sha candidate is treated as a valid digest cache entry`() = runBlocking {
        val root = Files.createTempDirectory("dlc-test")
        try {
            val cache = root.resolve("dlc").createDirectories()
            val bytes = "unused-uppercase".toByteArray()
            val uppercase = cache.resolve("${sha1(bytes).uppercase()}.SHA1").also { it.writeBytes(bytes) }

            val result = DownloadCacheCleanupService(
                cacheRoot = cache,
                versionsRoot = root.resolve("versions"),
                digestCalculator = ::calculateDigest,
            ).cleanup().getOrThrow()

            assertEquals(1, result.deletedFiles)
            assertFalse(uppercase.exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `sha256 and murmur2 content references are retained`() = runBlocking {
        val root = Files.createTempDirectory("dlc-test")
        try {
            val cache = root.resolve("dlc").createDirectories()
            val versions = root.resolve("versions").createDirectories()
            val bytes = "content-with-whitespace\n".toByteArray()
            val local = versions.resolve("0123456789abcdef01234567_pack").resolve("mods").createDirectories()
                .resolve("manual.jar").also { it.writeBytes(bytes) }
            val sha256 = digest(bytes, "SHA-256")
            val murmur = local.murmur2.toULong().toString()
            cache.resolve("$sha256.sha256").writeBytes(bytes)
            cache.resolve("$murmur.murmur2").writeBytes(bytes)

            val result = DownloadCacheCleanupService(
                cacheRoot = cache,
                versionsRoot = versions,
                digestCalculator = { path, algorithm ->
                    ClientContentStore.shared.calculateDigestForMigration(path, algorithm)
                },
            ).cleanup().getOrThrow()

            assertEquals(0, result.deletedFiles)
            assertTrue(Files.exists(cache.resolve("$sha256.sha256")))
            assertTrue(Files.exists(cache.resolve("$murmur.murmur2")))
            assertTrue(local.exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `reports monotonic structured progress through all cleanup phases`() = runBlocking {
        val root = Files.createTempDirectory("dlc-progress-test")
        try {
            val cache = root.resolve("dlc").createDirectories()
            val versions = root.resolve("versions").createDirectories()
            val bytes = "local-content".toByteArray()
            cache.resolve("${sha1(bytes)}.sha1").writeBytes(bytes)
            cache.resolve("${sha1("unused".toByteArray())}.sha1").writeBytes("unused".toByteArray())
            versions.resolve("0123456789abcdef01234567_pack").resolve("mods").createDirectories()
                .resolve("manual.jar").writeBytes(bytes)
            val progress = mutableListOf<Task2Progress>()

            DownloadCacheCleanupService(
                cacheRoot = cache,
                versionsRoot = versions,
                digestCalculator = ::calculateDigest,
            ).cleanup(Task2Context(emitProgress = progress::add)).getOrThrow()

            assertTrue(progress.any { it.message.contains("下载缓存") && it.fraction == 0f })
            assertTrue(progress.any { it.message.contains("本地整合包") && it.completedItems == 1 })
            assertTrue(progress.any { it.message.contains("删除未使用") })
            assertEquals(1f, progress.last().fraction)
            assertTrue(progress.mapNotNull { it.fraction }.zipWithNext().all { (a, b) -> b >= a })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private companion object {
        suspend fun calculateDigest(path: Path, algorithm: ContentDigestAlgorithm): String {
            require(algorithm != ContentDigestAlgorithm.MURMUR2)
            val name = if (algorithm == ContentDigestAlgorithm.SHA1) "SHA-1" else "SHA-256"
            return MessageDigest.getInstance(name).digest(Files.readAllBytes(path))
                .joinToString("") { "%02x".format(it) }
        }

        fun sha1(bytes: ByteArray): String = bytes.sha1Digest

        fun digest(bytes: ByteArray, algorithm: String): String = MessageDigest.getInstance(algorithm).digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
