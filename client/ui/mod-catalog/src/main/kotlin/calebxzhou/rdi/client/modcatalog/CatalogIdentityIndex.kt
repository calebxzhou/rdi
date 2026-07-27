package calebxzhou.rdi.client.modcatalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import calebxzhou.rdi.client.modcatalog.database.ModCatalogDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal interface CatalogIdentityIndex : AutoCloseable {
    val unavailableCause: Throwable?

    suspend fun find(platform: ModPlatform, slug: String): CatalogIdentityRecord?

    suspend fun search(query: String, offset: Int, limit: Int): List<CatalogIdentityRecord>
}

internal class UnavailableIdentityIndex(
    override val unavailableCause: Throwable
) : CatalogIdentityIndex {
    override suspend fun find(platform: ModPlatform, slug: String) = null

    override suspend fun search(query: String, offset: Int, limit: Int) = emptyList<CatalogIdentityRecord>()

    override fun close() = Unit
}

internal class SqliteCatalogIdentityIndex private constructor(
    private val databaseFile: Path,
    private val database: ModCatalogDatabase,
    private val driver: JdbcSqliteDriver,
    private val dispatcher: CoroutineDispatcher
) : CatalogIdentityIndex {
    override val unavailableCause: Throwable? = null
    private val lock = ReentrantLock()
    private val closed = AtomicBoolean()

    override suspend fun find(platform: ModPlatform, slug: String): CatalogIdentityRecord? = query {
        val normalizedSlug = normalizeProjectSlug(slug)
        val identity = database.modCatalogQueries.selectIdentityByProject(
            platform.name,
            normalizedSlug,
            ::mapRow
        ).executeAsOneOrNull() ?: return@query null
        database.modCatalogQueries.selectProjectsByMcmodId(identity.mcmodId.toLong(), ::mapRow)
            .executeAsList()
            .toIdentityRecords()
            .singleOrNull()
    }

    override suspend fun search(query: String, offset: Int, limit: Int): List<CatalogIdentityRecord> = query {
        val normalized = normalizeSearchText(query)
        if (normalized.isEmpty()) return@query emptyList()
        database.modCatalogQueries.searchIdentity(
            normalizedQuery = normalized,
            prefixPattern = "$normalized%",
            resultLimit = limit.toLong(),
            resultOffset = offset.toLong(),
            mapper = ::mapRow
        ).executeAsList().toIdentityRecords()
    }

    override fun close() {
        lock.withLock {
            if (closed.compareAndSet(false, true)) driver.close()
        }
    }

    private suspend fun <T> query(block: () -> T): T = withContext(dispatcher) {
        lock.withLock {
            check(!closed.get()) { "Catalog identity index is closed" }
            try {
                block()
            } catch (cause: CatalogException) {
                throw cause
            } catch (cause: Exception) {
                throw CatalogException.IdentityDatabase("Cannot read $databaseFile", cause)
            }
        }
    }

    companion object {
        const val FORMAT_VERSION = 1L

        fun openBundled(materializationDir: Path, dispatcher: CoroutineDispatcher): Result<SqliteCatalogIdentityIndex> =
            runCatching {
                Files.createDirectories(materializationDir)
                val databaseFile = materializationDir.resolve("mod_catalog.db")
                val resource = SqliteCatalogIdentityIndex::class.java.getResourceAsStream("/mod_catalog.db")
                    ?: throw CatalogException.IdentityDatabase("Bundled mod_catalog.db is missing")
                val temporary = materializationDir.resolve("mod_catalog.db.tmp")
                resource.use { input ->
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.move(
                    temporary,
                    databaseFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                val driver = JdbcSqliteDriver(
                    "jdbc:sqlite:${databaseFile.toAbsolutePath().normalize().toUri()}?mode=ro"
                )
                try {
                    val database = ModCatalogDatabase(driver)
                    val metadata = database.modCatalogQueries.selectMetadata().executeAsOne()
                    if (metadata.format_version != FORMAT_VERSION) {
                        throw CatalogException.IdentityDatabase(
                            "Unsupported mod catalog format ${metadata.format_version}"
                        )
                    }
                    val actualCounts = Triple(
                        database.modCatalogQueries.countMods().executeAsOne(),
                        database.modCatalogQueries.countProjects().executeAsOne(),
                        database.modCatalogQueries.countSearchTerms().executeAsOne()
                    )
                    check(actualCounts == Triple(metadata.mod_count, metadata.project_count, metadata.search_term_count)) {
                        "Catalog metadata counts do not match the database"
                    }
                    SqliteCatalogIdentityIndex(databaseFile, database, driver, dispatcher)
                } catch (cause: Exception) {
                    driver.close()
                    throw cause
                }
            }
    }
}

private data class IdentityRow(
    val mcmodId: Int,
    val name: String,
    val nameCn: String?,
    val intro: String?,
    val logoUrl: String?,
    val platform: ModPlatform?,
    val slug: String?,
    val nameCnOverride: String?
)

private fun mapRow(
    mcmodId: Long,
    name: String,
    nameCn: String?,
    intro: String?,
    logoUrl: String?,
    platform: String?,
    slug: String?,
    nameCnOverride: String?
) = IdentityRow(
    mcmodId = mcmodId.toInt(),
    name = name,
    nameCn = nameCn,
    intro = intro,
    logoUrl = logoUrl,
    platform = platform?.let(ModPlatform::valueOf),
    slug = slug,
    nameCnOverride = nameCnOverride
)

private fun List<IdentityRow>.toIdentityRecords(): List<CatalogIdentityRecord> =
    groupBy(IdentityRow::mcmodId).values.map { rows ->
        val first = rows.first()
        CatalogIdentityRecord(
            mcmodId = first.mcmodId,
            name = first.name,
            nameCn = first.nameCn,
            intro = first.intro,
            logoUrl = first.logoUrl,
            projects = rows.mapNotNull { row ->
                val platform = row.platform ?: return@mapNotNull null
                val slug = row.slug ?: return@mapNotNull null
                CatalogIdentityProject(platform, slug, row.nameCnOverride)
            }
        )
    }

internal fun normalizeSearchText(value: String): String = value
    .trim()
    .lowercase()
    .filter(Char::isLetterOrDigit)

internal fun normalizeProjectSlug(value: String): String = value.trim().lowercase()
