package calebxzhou.rdi.client.service.content

import calebxzhou.rdi.common.model.Task2Context
import calebxzhou.rdi.common.model.Task2
import calebxzhou.rdi.common.model.Task2Entry
import calebxzhou.rdi.common.model.Task2Progress
import calebxzhou.rdi.common.model.Task2Snapshot
import calebxzhou.rdi.common.model.Task2Status
import calebxzhou.rdi.common.service.murmur2
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientContentMigratorTest {
    @Test
    fun `has migratable files only sees direct supported legacy files`() {
        val missingModRoot = Files.createTempDirectory("rdi-missing-mods").resolve("dl-mods")
        val missingPackRoot = Files.createTempDirectory("rdi-missing-packs").resolve("dl-packs")
        val missingMigrator = ClientContentMigrator(
            sourceRoot = missingModRoot,
            destinationRoot = Files.createTempDirectory("rdi-missing-dlc"),
            packSourceRoot = missingPackRoot,
        )
        assertFalse(missingMigrator.hasMigratableFiles())
        assertEquals(
            ClientContentMigrationAvailability(false, false),
            missingMigrator.migrationAvailability()
        )

        val sourceRoot = Files.createTempDirectory("rdi-migratable-mods")
        val packSourceRoot = Files.createTempDirectory("rdi-migratable-packs")
        val migrator = ClientContentMigrator(
            sourceRoot = sourceRoot,
            destinationRoot = Files.createTempDirectory("rdi-migratable-dlc"),
            packSourceRoot = packSourceRoot,
        )
        assertFalse(migrator.hasMigratableFiles())

        write(sourceRoot, "readme.txt", "ignored".toByteArray())
        Files.createDirectories(sourceRoot.resolve("nested"))
        write(sourceRoot.resolve("nested"), "nested.jar", "ignored".toByteArray())
        write(packSourceRoot, "readme.txt", "ignored".toByteArray())
        Files.createDirectories(packSourceRoot.resolve("nested"))
        write(packSourceRoot.resolve("nested"), "nested.zip", "ignored".toByteArray())
        assertFalse(migrator.hasMigratableFiles())

        val modSource = write(sourceRoot, "mod.JAR", "mod".toByteArray())
        assertTrue(migrator.hasMigratableFiles())
        assertEquals(
            ClientContentMigrationAvailability(true, true),
            migrator.migrationAvailability()
        )
        Files.delete(modSource)

        val zipSource = write(packSourceRoot, "pack.ZIP", "zip".toByteArray())
        assertTrue(migrator.hasMigratableFiles())
        assertEquals(
            ClientContentMigrationAvailability(true, false),
            migrator.migrationAvailability()
        )
        Files.delete(zipSource)
        write(packSourceRoot, "pack.TAR.ZST", "tar".toByteArray())
        assertTrue(migrator.hasMigratableFiles())
    }

    @Test
    fun `mod migration progress only follows active mod migration task`() {
        for (status in listOf(Task2Status.QUEUED, Task2Status.RUNNING)) {
            assertTrue(
                isClientModMigrationInProgress(
                    listOf(entry("mod", CLIENT_CONTENT_MOD_MIGRATION_DEDUPE_KEY, status))
                )
            )
        }
        for (status in listOf(Task2Status.DONE, Task2Status.FAILED, Task2Status.CANCELLED)) {
            assertFalse(
                isClientModMigrationInProgress(
                    listOf(entry("mod", CLIENT_CONTENT_MOD_MIGRATION_DEDUPE_KEY, status))
                )
            )
        }
        assertFalse(
            isClientModMigrationInProgress(
                listOf(entry("pack", CLIENT_CONTENT_MIGRATION_DEDUPE_KEY, Task2Status.RUNNING))
            )
        )
    }

    @Test
    fun `new github sha256 names migrate with a legacy sha1 alias`() = runBlocking {
        val sourceRoot = Files.createTempDirectory("rdi-new-github-mods")
        val packSourceRoot = Files.createTempDirectory("rdi-new-github-packs")
        val destinationRoot = Files.createTempDirectory("rdi-new-github-dlc")
        val bytes = "github-sha256-content".toByteArray()
        val sha1Hash = sha1(bytes)
        val sha256Hash = sha256(bytes)
        val source = write(sourceRoot, "owner_with_github_${sha256Hash}.jar", bytes)

        val summary = ClientContentMigrator(
            sourceRoot = sourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { })

        assertEquals(1, summary.migrated)
        assertFalse(Files.exists(source))
        assertTrue(Files.isRegularFile(destinationRoot.resolve("$sha256Hash.sha256")))
        assertTrue(Files.isRegularFile(destinationRoot.resolve("$sha1Hash.sha1")))
        assertTrue(
            Files.isSameFile(
                destinationRoot.resolve("$sha256Hash.sha256"),
                destinationRoot.resolve("$sha1Hash.sha1"),
            )
        )
    }

    @Test
    fun `parser uses rightmost platform marker and recognizes legacy names`() {
        val parsed = ClientContentMigrator.parseFileName("a_cf_name_with_mr_0123456789012345678901234567890123456789.JAR")
        assertNotNull(parsed)
        assertEquals(LegacyContentPlatform.MODRINTH, parsed.platform)
        assertEquals("0123456789012345678901234567890123456789", parsed.embeddedHash)

        val legacy = ClientContentMigrator.parseFileName("slug_with_underscores_CF_123.jar")
        assertNotNull(legacy)
        assertEquals(LegacyContentPlatform.CURSEFORGE, legacy.platform)
        assertEquals("123", legacy.embeddedHash)

        assertEquals(
            LegacyContentPlatform.GITHUB,
            ClientContentMigrator.parseFileName(
                "owner_with_under_github_0123456789012345678901234567890123456789.jar"
            )?.platform
        )
        assertEquals(
            LegacyContentPlatform.RDI_CORE,
            ClientContentMigrator.parseFileName("rdi-5-mc-client-1.21.1-neoforge.JaR")?.platform
        )
        assertEquals(null, ClientContentMigrator.parseFileName("not-a-mod.jar"))
    }

    @Test
    fun `valid entries move and mismatched or unknown entries remain`() = runBlocking {
        val sourceRoot = Files.createTempDirectory("rdi-old-mods")
        val packSourceRoot = Files.createTempDirectory("rdi-old-packs")
        val destinationRoot = Files.createTempDirectory("rdi-dlc")
        val cfBytes = "curseforge-content".toByteArray()
        val mrBytes = "modrinth-content".toByteArray()
        val githubBytes = "github-content".toByteArray()
        val coreBytes = "rdi-core-content".toByteArray()

        val cfFingerprint = Files.createTempFile(sourceRoot, "cf-fingerprint", ".bin")
        Files.write(cfFingerprint, cfBytes)
        val cfHash = cfFingerprint.murmur2.toString()
        Files.delete(cfFingerprint)
        write(sourceRoot, "slug_with_cf_${cfHash}.jar", cfBytes)
        val mrHash = sha1(mrBytes)
        write(sourceRoot, "slug_with_mr_${mrHash}.jar", mrBytes)
        val githubHash = sha1(githubBytes)
        write(sourceRoot, "owner_with_github_${githubHash}.jar", githubBytes)
        write(sourceRoot, "rdi-5-mc-client-1.21.1-neoforge.jar", coreBytes)
        val mismatch = write(sourceRoot, "bad_mr_${"0".repeat(40)}.jar", "wrong".toByteArray())
        val unknown = write(sourceRoot, "some-random.jar", "unknown".toByteArray())
        Files.createDirectories(sourceRoot.resolve("nested"))
        write(sourceRoot.resolve("nested"), "nested_mr_${sha1("nested".toByteArray())}.jar", "nested".toByteArray())

        val summary = ClientContentMigrator(
            sourceRoot = sourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { })

        assertEquals(4, summary.migrated)
        assertEquals(2, summary.skipped)
        assertFalse(Files.exists(sourceRoot.resolve("slug_with_cf_${cfHash}.jar")))
        assertFalse(Files.exists(sourceRoot.resolve("slug_with_mr_${mrHash}.jar")))
        assertFalse(Files.exists(sourceRoot.resolve("owner_with_github_${githubHash}.jar")))
        assertTrue(Files.exists(mismatch))
        assertTrue(Files.exists(unknown))
        assertTrue(Files.exists(sourceRoot.resolve("nested/nested_mr_${sha1("nested".toByteArray())}.jar")))
        assertTrue(Files.isRegularFile(destinationRoot.resolve("${cfHash}.murmur2")))
        assertTrue(Files.isRegularFile(destinationRoot.resolve("${mrHash}.sha1")))
        val githubSha256 = sha256(githubBytes)
        assertTrue(Files.isRegularFile(destinationRoot.resolve("$githubSha256.sha256")))
        assertTrue(Files.isRegularFile(destinationRoot.resolve("$githubHash.sha1")))
        assertTrue(
            Files.isSameFile(
                destinationRoot.resolve("$githubSha256.sha256"),
                destinationRoot.resolve("$githubHash.sha1")
            )
        )
        val coreHash = sha1(coreBytes)
        assertContentEquals(coreBytes, Files.readAllBytes(destinationRoot.resolve("$coreHash.sha1")))
    }

    @Test
    fun `valid duplicate removes source and corrupt destination is replaced`() = runBlocking {
        val sourceRoot = Files.createTempDirectory("rdi-old-mods-duplicate")
        val packSourceRoot = Files.createTempDirectory("rdi-old-packs-duplicate")
        val destinationRoot = Files.createTempDirectory("rdi-dlc-duplicate")
        val payload = "duplicate-content".toByteArray()
        val hash = sha1(payload)
        val source = write(sourceRoot, "duplicate_mr_${hash}.jar", payload)
        Files.write(destinationRoot.resolve("$hash.sha1"), payload)

        val duplicateSummary = ClientContentMigrator(
            sourceRoot = sourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        )
            .migrate(Task2Context { })
        assertEquals(1, duplicateSummary.migrated)
        assertFalse(Files.exists(source))

        val replacement = "replacement-content".toByteArray()
        val replacementHash = sha1(replacement)
        val replacementSource = write(sourceRoot, "replacement_mr_${replacementHash}.jar", replacement)
        Files.write(destinationRoot.resolve("$replacementHash.sha1"), "corrupt".toByteArray())
        ClientContentMigrator(
            sourceRoot = sourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { })
        assertFalse(Files.exists(replacementSource))
        assertContentEquals(replacement, Files.readAllBytes(destinationRoot.resolve("$replacementHash.sha1")))
    }

    @Test
    fun `pack migration accepts direct zip and tar zst while excluding other and nested files`() = runBlocking {
        val modSourceRoot = Files.createTempDirectory("rdi-old-mods-pack-filter")
        val packSourceRoot = Files.createTempDirectory("rdi-old-packs-filter")
        val destinationRoot = Files.createTempDirectory("rdi-dlc-pack-filter")
        val zipBytes = "legacy-zip".toByteArray()
        val tarBytes = "legacy-tar-zst".toByteArray()
        val zipSource = write(packSourceRoot, "legacy.ZIP", zipBytes)
        val tarSource = write(packSourceRoot, "legacy.TAR.ZST", tarBytes)
        val otherSource = write(packSourceRoot, "readme.txt", "not-cacheable".toByteArray())
        Files.createDirectories(packSourceRoot.resolve("nested"))
        val nestedSource = write(packSourceRoot.resolve("nested"), "nested.zip", "nested".toByteArray())
        val progress = mutableListOf<Task2Progress>()

        val summary = ClientContentMigrator(
            sourceRoot = modSourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { progress.add(it) })

        assertEquals(2, summary.migrated)
        assertEquals(0, summary.skipped)
        assertEquals(2, summary.totalItems)
        assertEquals((zipBytes.size + tarBytes.size).toLong(), summary.totalBytes)
        assertEquals(summary.totalBytes, summary.completedBytes)
        assertFalse(Files.exists(zipSource))
        assertFalse(Files.exists(tarSource))
        assertTrue(Files.exists(otherSource))
        assertTrue(Files.exists(nestedSource))
        assertContentEquals(zipBytes, Files.readAllBytes(destinationRoot.resolve("${sha1(zipBytes)}.sha1")))
        assertContentEquals(tarBytes, Files.readAllBytes(destinationRoot.resolve("${sha1(tarBytes)}.sha1")))
        assertEquals(2, progress.last().completedItems)
        assertEquals(2, progress.last().totalItems)
        assertEquals(summary.totalBytes, progress.last().totalBytes)
    }

    @Test
    fun `pack duplicate removes source and corrupt destination is replaced`() = runBlocking {
        val modSourceRoot = Files.createTempDirectory("rdi-old-mods-pack-duplicate")
        val packSourceRoot = Files.createTempDirectory("rdi-old-packs-duplicate-only")
        val destinationRoot = Files.createTempDirectory("rdi-dlc-pack-duplicate")
        val duplicateBytes = "duplicate-pack".toByteArray()
        val duplicateHash = sha1(duplicateBytes)
        val duplicateSource = write(packSourceRoot, "duplicate.tAr.ZsT", duplicateBytes)
        Files.write(destinationRoot.resolve("$duplicateHash.sha1"), duplicateBytes)

        val duplicateSummary = ClientContentMigrator(
            sourceRoot = modSourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { })
        assertEquals(1, duplicateSummary.migrated)
        assertFalse(Files.exists(duplicateSource))

        val replacementBytes = "replacement-pack".toByteArray()
        val replacementHash = sha1(replacementBytes)
        val replacementSource = write(packSourceRoot, "replacement.zip", replacementBytes)
        Files.write(destinationRoot.resolve("$replacementHash.sha1"), "corrupt".toByteArray())

        val replacementSummary = ClientContentMigrator(
            sourceRoot = modSourceRoot,
            destinationRoot = destinationRoot,
            packSourceRoot = packSourceRoot,
        ).migrate(Task2Context { })
        assertEquals(1, replacementSummary.migrated)
        assertFalse(Files.exists(replacementSource))
        assertContentEquals(replacementBytes, Files.readAllBytes(destinationRoot.resolve("$replacementHash.sha1")))
    }

    private fun write(root: Path, name: String, bytes: ByteArray): Path =
        root.resolve(name).also { Files.write(it, bytes) }

    private fun sha1(bytes: ByteArray): String = digest(bytes, "SHA-1")

    private fun sha256(bytes: ByteArray): String = digest(bytes, "SHA-256")

    private fun digest(bytes: ByteArray, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    private fun entry(runId: String, dedupeKey: String, status: Task2Status) = Task2Entry(
        runId = runId,
        task = Task2.Leaf(title = runId, id = runId) { _ -> },
        dedupeKey = dedupeKey,
        snapshot = Task2Snapshot(status = status),
    )
}
