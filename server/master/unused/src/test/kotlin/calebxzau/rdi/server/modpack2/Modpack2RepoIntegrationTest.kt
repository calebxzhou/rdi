package calebxzau.rdi.server.modpack2

import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2Sort
import calebxzau.rdi.common.model.Modpack2Version
import calebxzau.rdi.common.model.Modpack2VersionStatus
import calebxzau.rdi.server.modpack.Modpack2Service
import calebxzau.rdi.server.modpack.Modpack2UploadResult
import calebxzau.rdi.common.model.ModpackCategory
import calebxzhou.rdi.master.PostgresConfig
import calebxzau.rdi.server.account.InsertIfMissingResult
import calebxzau.rdi.server.account.AccountRecord
import calebxzau.rdi.server.account.PgAccountRepo
import calebxzau.rdi.server.modpack.Modpack2Repo
import calebxzhou.rdi.master.infra.postgres.DatabaseProvider
import kotlinx.coroutines.test.runTest
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.toJavaUuid

@Testcontainers
class Modpack2RepoIntegrationTest {
    private lateinit var database: DatabaseProvider
    private val repository = Modpack2Repo()
    private val accounts = PgAccountRepo()

    @BeforeTest
    fun setUp() {
        database = DatabaseProvider(
            PostgresConfig(
                jdbcUrl = postgres.jdbcUrl,
                username = postgres.username,
                password = postgres.password,
                maximumPoolSize = 4
            )
        )
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun `Flyway V5 is applied and database generates UUIDv7 ids`() = runTest {
        assertTrue(
            database.transaction {
                exec("SELECT success FROM flyway_schema_history WHERE version = '5'") { result ->
                    result.next() && result.getBoolean(1)
                }
            } == true
        )
        assertTrue(
            database.transaction {
                exec(
                    """
                    SELECT to_regclass('public.host2_content_revision') IS NOT NULL
                       AND to_regclass('public.host2_content_snapshot') IS NOT NULL
                       AND to_regclass('public.host2_operation') IS NOT NULL
                       AND to_regclass('public.host2_delete_tombstone') IS NOT NULL
                       AND to_regclass('public.host2_mod') IS NULL
                    """.trimIndent()
                ) { result ->
                    result.next() && result.getBoolean(1)
                }
            } == true
        )
        assertTrue(
            database.transaction {
                exec(
                    """
                    SELECT to_regclass('public.modpack_upload_result') IS NOT NULL
                       AND to_regclass('public.modpack_name_lower_unique') IS NOT NULL
                       AND to_regclass('public.modpack_version_name_lower_unique') IS NOT NULL
                       AND to_regclass('public.modpack_upload_result_expiry_idx') IS NOT NULL
                    """.trimIndent()
                ) { result ->
                    result.next() && result.getBoolean(1)
                }
            } == true
        )

        val ownerId = createAccount()
        val modpack = createModpack(ownerId)
        assertEquals(7, modpack.id.version())

        val version = insertVersion(modpack.id, Modpack2VersionStatus.Building)
        assertEquals(7, version.id.version())
    }

    @Test
    fun `account foreign keys enforce owner and clear deleted uploader`() = runTest {
        val ownerId = createAccount()
        val uploaderId = createAccount()
        val modpack = createModpack(ownerId)
        val version = insertVersion(
            modpack.id,
            status = Modpack2VersionStatus.Building,
            uploaderId = uploaderId
        )

        val missingOwnerFailure = failureOrNull {
            database.transaction {
                repository.insertModpack(
                    ownerId = UUID.randomUUID(),
                    name = "missing-owner-${UUID.randomUUID().toString().take(8)}",
                    intro = "A valid modpack introduction",
                    mc = 21,
                    loader = Modpack2Loader.NeoForge,
                    iconUrl = "https://example.com/icon.png"
                )
            }
        }
        assertNotNull(missingOwnerFailure)

        assertEquals(1, deleteAccount(uploaderId))
        assertNull(database.transaction { repository.findVersion(version.id)?.uploaderId })

        assertNotNull(failureOrNull { deleteAccount(ownerId) })
        assertEquals(ownerId, database.transaction { repository.findModpack(modpack.id)?.ownerId })
    }

    @Test
    fun `only one Building version is allowed and status transitions are compare and set`() = runTest {
        val modpack = createModpack(createAccount())
        val building = insertVersion(modpack.id, Modpack2VersionStatus.Building, name = "building")

        assertNotNull(
            failureOrNull {
                insertVersion(modpack.id, Modpack2VersionStatus.Building, name = "second-building")
            }
        )

        val failed = insertVersion(modpack.id, Modpack2VersionStatus.Fail, name = "retry")
        assertTrue(repository.transitionBuildingToFailInDatabase(building.id))
        assertTrue(repository.transitionFailToBuildingInDatabase(failed.id))
        assertEquals(Modpack2VersionStatus.Building, findVersion(failed.id)?.status)
        assertTrue(repository.transitionFailToBuildingInDatabase(failed.id).not())
        assertTrue(repository.transitionBuildingToFailInDatabase(failed.id))
        assertTrue(repository.transitionFailToBuildingInDatabase(building.id))
        assertTrue(repository.transitionBuildingToOkInDatabase(building.id, 128))
        assertEquals(Modpack2VersionStatus.Ok, findVersion(building.id)?.status)
        assertEquals(128L, findVersion(building.id)?.totalSize)
        assertTrue(repository.transitionBuildingToFailInDatabase(building.id).not())
    }

    @Test
    fun `current version uses greatest UUIDv7 Ok and falls back after deletion`() = runTest {
        val modpack = createModpack(createAccount())
        val older = insertVersion(modpack.id, Modpack2VersionStatus.Ok, name = "older", totalSize = 1)
        Thread.sleep(10)
        val newer = insertVersion(modpack.id, Modpack2VersionStatus.Ok, name = "newer", totalSize = 2)
        assertEquals(7, older.id.version())
        assertEquals(7, newer.id.version())
        assertTrue(older.id.compareTo(newer.id) < 0)

        assertEquals(newer.id, database.transaction { repository.findCurrentVersion(modpack.id)?.id })
        assertTrue(database.transaction { repository.deleteVersion(newer.id) })
        assertEquals(older.id, database.transaction { repository.findCurrentVersion(modpack.id)?.id })
    }

    @Test
    fun `play count increments as Int and content plus dependent rows cascade`() = runTest {
        val modpack = createModpack(createAccount())
        val version = insertVersion(modpack.id, Modpack2VersionStatus.Ok, name = "content", totalSize = 12)

        assertEquals(0, database.transaction { repository.findStats(modpack.id)?.playCount })
        assertTrue(database.transaction { repository.incrementPlayCount(modpack.id, 3) })
        assertTrue(database.transaction { repository.incrementPlayCount(modpack.id) })
        assertEquals(4, database.transaction { repository.findStats(modpack.id)?.playCount })

        database.transaction {
            repository.replaceCategories(modpack.id, listOf(ModpackCategory.Tech))
            repository.replaceContents(
                version.id,
                listOf(
                    Modpack2Content(
                        versionId = version.id,
                        platform = ContentPlatform.Modrinth,
                        type = ContentType.Mod,
                        projectId = "project",
                        fileId = "file",
                        slug = "example-mod",
                        hash = "sha1",
                        targetPath = null,
                        side = ContentSide.Client,
                        required = true,
                        fileSize = 12
                    )
                )
            )
        }
        assertNull(database.transaction { repository.listContents(version.id).single().targetPath })

        assertTrue(database.transaction { repository.deleteModpack(modpack.id) })
        assertNull(database.transaction { repository.findModpack(modpack.id) })
        assertNull(database.transaction { repository.findStats(modpack.id) })
        assertTrue(database.transaction { repository.listCategories(modpack.id).isEmpty() })
        assertTrue(database.transaction { repository.listVersions(modpack.id).isEmpty() })
        assertTrue(database.transaction { repository.listContents(version.id).isEmpty() })
    }

    @Test
    fun `same project may retain distinct client and server artifacts`() = runTest {
        val modpack = createModpack(createAccount())
        val version = insertVersion(modpack.id, Modpack2VersionStatus.Ok, name = "dual-side")
        val client = Modpack2Content(
            versionId = version.id,
            platform = ContentPlatform.Modrinth,
            type = ContentType.Mod,
            projectId = "same-project",
            fileId = "client-file",
            slug = "same-project",
            hash = "a".repeat(40),
            side = ContentSide.Client,
            fileSize = 1,
        )
        val server = client.copy(
            fileId = "server-file",
            hash = "b".repeat(40),
            side = ContentSide.Server,
        )

        database.transaction { repository.replaceContents(version.id, listOf(client, server)) }

        val stored = database.transaction { repository.listContents(version.id) }
        assertEquals(setOf(ContentSide.Client, ContentSide.Server), stored.map { it.side }.toSet())
        assertEquals(setOf("client-file", "server-file"), stored.map { it.fileId }.toSet())
    }

    @Test
    fun `public and owned search applies visibility filters stable sorts and page cap`() = runTest {
        val token = "search-${UUID.randomUUID().toString().take(8)}"
        val ownerId = createAccount()
        val otherOwnerId = createAccount()
        val hiddenOwnerId = createAccount()

        val filterTarget = createNamedModpack(ownerId, "${token}-filter-target", ModpackCategory.Tech)
        createNamedModpack(ownerId, "${token}-filter-wrong-category", ModpackCategory.Magic)
        createNamedModpack(otherOwnerId, "${token}-filter-wrong-owner", ModpackCategory.Tech)

        val sortA = createNamedModpack(ownerId, "${token}-sort-a", ModpackCategory.Tech)
        Thread.sleep(10)
        val sortB = createNamedModpack(ownerId, "${token}-sort-b", ModpackCategory.Tech)
        Thread.sleep(10)
        val sortC = createNamedModpack(ownerId, "${token}-sort-c", ModpackCategory.Tech)

        database.transaction {
            repository.incrementPlayCount(sortA.id, 5)
            repository.incrementPlayCount(sortB.id, 5)
            repository.incrementPlayCount(sortC.id, 1)
        }

        database.transaction {
            buildList {
                repeat(101) { index ->
                    val modpack = repository.insertModpack(
                        ownerId = ownerId,
                        name = "${token}-limit-${index.toString().padStart(3, '0')}",
                        intro = "A valid modpack introduction",
                        mc = 21,
                        loader = Modpack2Loader.NeoForge,
                        iconUrl = "https://example.com/icon.png"
                    )
                    repository.insertVersion(
                        modpackId = modpack.id,
                        uploaderId = null,
                        name = "v",
                        changelog = "",
                        status = Modpack2VersionStatus.Ok,
                        totalSize = 1
                    )
                    add(modpack.id)
                }
            }
        }

        val hidden = createNamedModpack(
            hiddenOwnerId,
            "${token}-hidden",
            ModpackCategory.Tech,
            status = Modpack2VersionStatus.Building
        )

        val public = database.transaction {
            repository.searchPublic(keyword = token, limit = 1000)
        }
        assertFalse(public.items.any { it.modpack.id == hidden.id })
        assertNotNull(database.transaction { repository.findPublicDetail(filterTarget.id) })
        assertNull(database.transaction { repository.findPublicDetail(hidden.id) })

        val filtered = database.transaction {
            repository.searchPublic(
                keyword = "${token}-filter",
                category = ModpackCategory.Tech,
                ownerId = ownerId,
                limit = 100
            )
        }
        assertEquals(listOf(filterTarget.id), filtered.items.map { it.modpack.id })

        val updated = database.transaction {
            repository.searchPublic(
                keyword = "${token}-sort",
                category = ModpackCategory.Tech,
                ownerId = ownerId,
                sort = Modpack2Sort.Updated,
                limit = 100
            )
        }
        assertEquals(listOf(sortC.id, sortB.id, sortA.id), updated.items.map { it.modpack.id })

        val popular = database.transaction {
            repository.searchPublic(
                keyword = "${token}-sort",
                category = ModpackCategory.Tech,
                ownerId = ownerId,
                sort = Modpack2Sort.Popular,
                limit = 100
            )
        }
        assertEquals(listOf(sortB.id, sortA.id, sortC.id), popular.items.map { it.modpack.id })

        val byName = database.transaction {
            repository.searchPublic(
                keyword = "${token}-sort",
                category = ModpackCategory.Tech,
                ownerId = ownerId,
                sort = Modpack2Sort.Name,
                limit = 100
            )
        }
        assertEquals(listOf(sortA.id, sortB.id, sortC.id), byName.items.map { it.modpack.id })

        val capped = database.transaction {
            repository.searchPublic(keyword = "${token}-limit", limit = 1000)
        }
        assertEquals(100, capped.limit)
        assertEquals(100, capped.items.size)
        assertEquals(101L, capped.total)
        assertTrue(capped.hasMore)

        val owned = database.transaction { repository.listOwned(hiddenOwnerId, limit = 1000) }
        val ownedHidden = owned.items.single { it.modpack.id == hidden.id }
        assertNull(ownedHidden.currentVersion)
    }

    @Test
    fun `public list uses fixed pages updated order and only exposes Ok versions`() = runTest {
        val ownerId = createAccount()
        database.transaction {
            repeat(201) { index ->
                val modpack = repository.insertModpack(
                    ownerId = ownerId,
                    name = "public-page-${UUID.randomUUID()}-$index",
                    intro = "A valid modpack introduction",
                    mc = 21,
                    loader = Modpack2Loader.NeoForge,
                    iconUrl = "https://example.com/icon.png",
                )
                repository.insertVersion(
                    modpackId = modpack.id,
                    uploaderId = null,
                    name = "v",
                    changelog = "",
                    status = Modpack2VersionStatus.Ok,
                    totalSize = 1,
                )
            }
        }
        val buildingOnly = createNamedModpack(
            ownerId,
            "public-page-building-${UUID.randomUUID()}",
            status = Modpack2VersionStatus.Building,
        )
        val failOnly = createNamedModpack(
            ownerId,
            "public-page-fail-${UUID.randomUUID()}",
            status = Modpack2VersionStatus.Fail,
        )

        val page0 = database.transaction { repository.listPublic(offset = 0, limit = 100) }
        val page1 = database.transaction { repository.listPublic(offset = 100, limit = 100) }
        val expectedPage0 = database.transaction {
            repository.searchPublic(offset = 0, limit = 100).items
        }
        val expectedPage1 = database.transaction {
            repository.searchPublic(offset = 100, limit = 100).items
        }
        val servicePage0 = Modpack2Service(database, repository).listPublic(0)
        val servicePage1 = Modpack2Service(database, repository).listPublic(1)
        val serviceOutOfRange = Modpack2Service(database, repository).listPublic(10_000)
        assertEquals(100, page0.size)
        assertEquals(100, page1.size)
        assertEquals(expectedPage0.map { it.modpack.id }, page0.map { it.modpack.id })
        assertEquals(expectedPage1.map { it.modpack.id }, page1.map { it.modpack.id })
        assertEquals(page0.map { it.modpack.id }, servicePage0.map { it.id.toJavaUuid() })
        assertEquals(page1.map { it.modpack.id }, servicePage1.map { it.id.toJavaUuid() })
        assertTrue(serviceOutOfRange.isEmpty())
        assertTrue(
            page0.map { it.modpack.id }.intersect(page1.map { it.modpack.id }.toSet()).isEmpty()
        )
        val allPublic = buildList {
            var offset = 0L
            while (true) {
                val page = database.transaction { repository.listPublic(offset = offset, limit = 100) }
                if (page.isEmpty()) break
                addAll(page)
                offset += 100
            }
        }
        assertFalse(allPublic.any { it.modpack.id == buildingOnly.id })
        assertFalse(allPublic.any { it.modpack.id == failOnly.id })
        assertTrue(database.transaction { repository.listPublic(offset = Long.MAX_VALUE, limit = 100).isEmpty() })
    }

    @Test
    fun `upload result is idempotent until its expiry`() = runTest {
        val ownerId = createAccount()
        val modpack = createModpack(ownerId)
        val version = insertVersion(modpack.id, Modpack2VersionStatus.Building)
        val uploadId = UUID.randomUUID()
        val result = Modpack2UploadResult(
            uploadId = uploadId,
            ownerId = ownerId,
            modpackId = modpack.id,
            versionId = version.id,
            expiresAt = 20_000
        )

        assertTrue(database.transaction { repository.insertUploadResult(result) })
        assertFalse(database.transaction { repository.insertUploadResult(result) })
        assertEquals(
            result,
            database.transaction { repository.findUploadResult(uploadId, ownerId, 10_000) }
        )
        assertNull(database.transaction { repository.findUploadResult(uploadId, ownerId, 20_000) })
        assertEquals(1, database.transaction { repository.deleteExpiredUploadResults(20_000) })
    }

    private suspend fun createAccount(): UUID {
        val id = UUID.randomUUID()
        val result = database.transaction {
            accounts.insertIfMissing(
                AccountRecord(
                    id = id,
                    name = "integration-${id.toString().take(8)}",
                    pwd = "123456",
                    qq = accountSequence.getAndIncrement().toString(),
                    msid = null,
                    isSlim = true,
                    skin = "https://example.com/skin.png",
                    cape = null
                )
            )
        }
        assertEquals(InsertIfMissingResult.INSERTED, result)
        return id
    }

    private suspend fun createModpack(ownerId: UUID): Modpack2 =
        database.transaction {
            repository.insertModpack(
                ownerId = ownerId,
                name = "Integration Pack ${UUID.randomUUID().toString().take(8)}",
                intro = "A valid modpack introduction",
                mc = 21,
                loader = Modpack2Loader.NeoForge,
                iconUrl = "https://example.com/icon.png"
            )
        }

    private suspend fun createNamedModpack(
        ownerId: UUID,
        name: String,
        category: ModpackCategory? = null,
        status: Modpack2VersionStatus = Modpack2VersionStatus.Ok,
    ): Modpack2 {
        val modpack = database.transaction {
            repository.insertModpack(
                ownerId = ownerId,
                name = name,
                intro = "A valid modpack introduction",
                mc = 21,
                loader = Modpack2Loader.NeoForge,
                iconUrl = "https://example.com/icon.png"
            )
        }
        database.transaction {
            category?.let { repository.replaceCategories(modpack.id, listOf(it)) }
            repository.insertVersion(
                modpackId = modpack.id,
                uploaderId = null,
                name = "v",
                changelog = "",
                status = status,
                totalSize = if (status == Modpack2VersionStatus.Ok) 1 else 0
            )
        }
        return modpack
    }

    private suspend fun insertVersion(
        modpackId: UUID,
        status: Modpack2VersionStatus,
        uploaderId: UUID? = null,
        name: String = "version-${UUID.randomUUID().toString().take(8)}",
        totalSize: Long = 0
    ): Modpack2Version = database.transaction {
        repository.insertVersion(
            modpackId = modpackId,
            uploaderId = uploaderId,
            name = name,
            changelog = "",
            status = status,
            totalSize = totalSize
        )
    }

    private suspend fun findVersion(id: UUID): Modpack2Version? =
        database.transaction { repository.findVersion(id) }

    private suspend fun failureOrNull(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (error: Throwable) {
            error
        }

    private fun deleteAccount(id: UUID): Int =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement("DELETE FROM account WHERE id = ?").use { statement ->
                statement.setObject(1, id)
                statement.executeUpdate()
            }
        }

    private suspend fun Modpack2Repo.transitionFailToBuildingInDatabase(id: UUID): Boolean =
        database.transaction { transitionFailToBuilding(id) }

    private suspend fun Modpack2Repo.transitionBuildingToFailInDatabase(id: UUID): Boolean =
        database.transaction { transitionBuildingToFail(id) }

    private suspend fun Modpack2Repo.transitionBuildingToOkInDatabase(id: UUID, totalSize: Long): Boolean =
        database.transaction { transitionBuildingToOk(id, totalSize) }

    companion object {
        private val accountSequence = AtomicInteger(10000)

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:18")
            .withDatabaseName("rdi")
            .withUsername("rdi")
            .withPassword("rdi")
    }
}
