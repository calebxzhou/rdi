package calebxzau.rdi.server.modpack

import calebxzau.rdi.common.model.ContentPlatform
import calebxzau.rdi.common.model.ContentSide
import calebxzau.rdi.common.model.ContentType
import calebxzau.rdi.common.model.Modpack2
import calebxzau.rdi.common.model.Modpack2Category
import calebxzau.rdi.common.model.Modpack2Content
import calebxzau.rdi.common.model.Modpack2Detail
import calebxzau.rdi.common.model.Modpack2Loader
import calebxzau.rdi.common.model.Modpack2Page
import calebxzau.rdi.common.model.Modpack2RawFile
import calebxzau.rdi.common.model.Modpack2RawFileRoot
import calebxzau.rdi.common.model.Modpack2Search
import calebxzau.rdi.common.model.Modpack2ServerMode
import calebxzau.rdi.common.model.Modpack2Sort
import calebxzau.rdi.common.model.Modpack2Stats
import calebxzau.rdi.common.model.Modpack2Version
import calebxzau.rdi.common.model.Modpack2VersionStatus
import calebxzau.rdi.common.model.Modpack2VersionManifestDto
import calebxzhou.rdi.common.serdesJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import calebxzau.rdi.common.model.ModpackCategory
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.ResultSet
import java.util.UUID

/**
 * Synchronous persistence operations for the Modpack2 schema.
 *
 * The service owns the surrounding DatabaseProvider transaction. This class
 * deliberately does not open transactions so callers can compose row locks,
 * quota checks, and mutations atomically.
 */
class Modpack2Repo {
    /** Inserts a record using PostgreSQL's uuidv7() default for its id. */
    fun insertModpack(
        ownerId: UUID,
        name: String,
        intro: String,
        mc: Int,
        loader: Modpack2Loader,
        iconUrl: String,
        sourceUrl: String? = null,
    ): Modpack2 {
        val inserted = ModpackTable.insertReturning {
            it[ModpackTable.name] = name.trim()
            it[ModpackTable.ownerId] = ownerId
            it[ModpackTable.intro] = intro.trim()
            it[ModpackTable.mc] = mc.toShort()
            it[ModpackTable.loader] = loader.name
            it[ModpackTable.iconUrl] = iconUrl.trim()
            it[ModpackTable.sourceUrl] = sourceUrl?.trim()?.ifBlank { null }
        }.single().toModpack()
        insertDefaultStats(inserted.id)
        return inserted
    }

    fun findModpack(id: UUID): Modpack2? =
        ModpackTable.selectAll()
            .where { ModpackTable.id eq id }
            .singleOrNull()
            ?.toModpack()

    fun findModpackByNameIgnoreCase(name: String): Modpack2? {
        val id = TransactionManager.current().exec(
            "SELECT id FROM modpack WHERE LOWER(name) = LOWER(?) LIMIT 1",
            listOf(textArgument(name.trim()))
        ) { result ->
            if (result.next()) result.getObject(1) as UUID else null
        } ?: return null
        return findModpack(id)
    }

    fun findModpackForUpdate(id: UUID): Modpack2? =
        ModpackTable.selectAll()
            .where { ModpackTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toModpack()

    fun updateModpackInfo(
        id: UUID,
        name: String,
        intro: String,
        iconUrl: String,
        sourceUrl: String?,
    ): Boolean = ModpackTable.update({ ModpackTable.id eq id }) {
        it[ModpackTable.name] = name.trim()
        it[ModpackTable.intro] = intro.trim()
        it[ModpackTable.iconUrl] = iconUrl.trim()
        it[ModpackTable.sourceUrl] = sourceUrl?.trim()?.ifBlank { null }
    } == 1

    fun transferOwnership(id: UUID, ownerId: UUID): Boolean =
        ModpackTable.update({ ModpackTable.id eq id }) {
            it[ModpackTable.ownerId] = ownerId
        } == 1

    fun deleteModpack(id: UUID): Boolean =
        ModpackTable.deleteWhere { ModpackTable.id eq id } == 1

    fun countByOwner(ownerId: UUID): Long =
        ModpackTable.selectAll()
            .where { ModpackTable.ownerId eq ownerId }
            .count()

    fun searchPublic(query: Modpack2Search): Modpack2Page<Modpack2Detail> = searchPublic(
        keyword = query.keyword,
        category = query.category,
        ownerId = query.ownerId,
        sort = query.sort,
        offset = query.offset,
        limit = query.limit,
    )

    /**
     * Searches only modpacks with an Ok version. Filters are combined with
     * AND; a keyword is applied to both name and intro with ILIKE.
     */
    fun searchPublic(
        keyword: String? = null,
        category: ModpackCategory? = null,
        ownerId: UUID? = null,
        sort: Modpack2Sort = Modpack2Sort.Updated,
        offset: Long = 0,
        limit: Int = 50,
    ): Modpack2Page<Modpack2Detail> {
        val page = normalizePage(offset, limit)
        val filter = publicFilter(keyword, category, ownerId)
        val rows = queryDetails(
            where = "${publicVisibilitySql()} AND ${filter.sql}",
            args = filter.args,
            sort = sort,
            includeOnlyOk = true,
            page = page,
        )
        val total = countDetails(
            where = "${publicVisibilitySql()} AND ${filter.sql}",
            args = filter.args,
        )
        return pageResult(rows, total, page)
    }

    /** Lists the public Modpack2 projection without a total-count query. */
    fun listPublic(
        offset: Long = 0,
        limit: Int = MAX_PAGE_SIZE,
    ): List<Modpack2Detail> {
        val page = normalizePage(offset, limit)
        return queryDetails(
            where = publicVisibilitySql(),
            args = emptyList(),
            sort = Modpack2Sort.Updated,
            includeOnlyOk = true,
            page = page,
        )
    }

    /** Lists all owned Modpack rows, regardless of their version statuses. */
    fun listOwned(
        ownerId: UUID,
        offset: Long = 0,
        limit: Int = 50,
    ): Modpack2Page<Modpack2Detail> {
        val page = normalizePage(offset, limit)
        val filter = SqlFilter(
            sql = "m.owner_id = ?",
            args = listOf(uuidArgument(ownerId)),
        )
        val rows = queryDetails(
            where = filter.sql,
            args = filter.args,
            sort = Modpack2Sort.Updated,
            includeOnlyOk = false,
            page = page,
            ownedOrder = true,
        )
        val total = countDetails(filter.sql, filter.args)
        return pageResult(rows, total, page)
    }

    /** Returns one detail projection and its greatest Ok version, if present. */
    fun findDetail(id: UUID): Modpack2Detail? {
        val rows = queryDetails(
            where = "m.id = ?",
            args = listOf(uuidArgument(id)),
            sort = Modpack2Sort.Updated,
            includeOnlyOk = false,
            page = PageWindow(offset = 0, limit = 1),
        )
        return rows.singleOrNull()
    }

    fun findPublicDetail(id: UUID): Modpack2Detail? =
        findDetail(id)?.takeIf { it.currentVersion != null }

    fun findOwnedDetail(id: UUID, ownerId: UUID): Modpack2Detail? =
        findDetail(id)?.takeIf { it.modpack.ownerId == ownerId }

    fun listCategories(modpackId: UUID): List<Modpack2Category> =
        ModpackCategoryTable.selectAll()
            .where { ModpackCategoryTable.modpackId eq modpackId }
            .orderBy(ModpackCategoryTable.category, SortOrder.ASC)
            .map { it.toModpackCategory() }

    fun replaceCategories(modpackId: UUID, categories: Iterable<ModpackCategory>) {
        ModpackCategoryTable.deleteWhere { ModpackCategoryTable.modpackId eq modpackId }
        categories.distinct().forEach { category ->
            ModpackCategoryTable.insert {
                it[ModpackCategoryTable.modpackId] = modpackId
                it[ModpackCategoryTable.category] = category.name
            }
        }
    }

    fun findStats(modpackId: UUID): Modpack2Stats? =
        ModpackStatsTable.selectAll()
            .where { ModpackStatsTable.modpackId eq modpackId }
            .singleOrNull()
            ?.toModpackStats()

    fun incrementPlayCount(modpackId: UUID, amount: Int = 1): Boolean {
        require(amount >= 0) { "play count increment must be non-negative" }
        return ModpackStatsTable.update({ ModpackStatsTable.modpackId eq modpackId }) {
            it[ModpackStatsTable.playCount] = ModpackStatsTable.playCount + amount
        } == 1
    }

    fun addPlayTimeSec(modpackId: UUID, amount: Long): Boolean {
        require(amount >= 0) { "play time increment must be non-negative" }
        return ModpackStatsTable.update({ ModpackStatsTable.modpackId eq modpackId }) {
            it[ModpackStatsTable.playTimeSec] = ModpackStatsTable.playTimeSec + amount
        } == 1
    }

    /** Inserts a version using PostgreSQL's uuidv7() default for its id. */
    fun insertVersion(
        modpackId: UUID,
        uploaderId: UUID?,
        name: String,
        changelog: String,
        status: Modpack2VersionStatus,
        totalSize: Long = 0,
        serverMode: Modpack2ServerMode = Modpack2ServerMode.Generated,
        baseVersionId: UUID? = null,
    ): Modpack2Version =
        ModpackVersionTable.insertReturning {
            it[ModpackVersionTable.modpackId] = modpackId
            it[ModpackVersionTable.uploaderId] = uploaderId
            it[ModpackVersionTable.name] = name.trim()
            it[ModpackVersionTable.changelog] = changelog
            it[ModpackVersionTable.status] = status.name
            it[ModpackVersionTable.totalSize] = totalSize
            it[ModpackVersionTable.serverMode] = serverMode.name
            it[ModpackVersionTable.baseVersionId] = baseVersionId
        }.single().toModpackVersion()

    fun insertVersionManifest(versionId: UUID, manifest: Modpack2VersionManifestDto) {
        ModpackVersionManifestTable.insert {
            it[ModpackVersionManifestTable.versionId] = versionId
            it[ModpackVersionManifestTable.format] = manifest.format.name
            it[ModpackVersionManifestTable.manifestJson] = manifest.manifestJson
            it[ModpackVersionManifestTable.bindingsJson] = serdesJson.encodeToString(manifest.bindings)
        }
    }

    fun findVersionManifest(versionId: UUID): Modpack2VersionManifestDto? =
        ModpackVersionManifestTable.selectAll()
            .where { ModpackVersionManifestTable.versionId eq versionId }
            .singleOrNull()
            ?.let { row ->
                Modpack2VersionManifestDto(
                    format = calebxzau.rdi.common.model.Modpack2ManifestFormat.valueOf(
                        row[ModpackVersionManifestTable.format]
                    ),
                    manifestJson = row[ModpackVersionManifestTable.manifestJson],
                    bindings = serdesJson.decodeFromString(row[ModpackVersionManifestTable.bindingsJson]),
                )
            }

    fun findVersion(id: UUID): Modpack2Version? =
        ModpackVersionTable.selectAll()
            .where { ModpackVersionTable.id eq id }
            .singleOrNull()
            ?.toModpackVersion()

    fun findVersionForUpdate(id: UUID): Modpack2Version? =
        ModpackVersionTable.selectAll()
            .where { ModpackVersionTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.toModpackVersion()

    fun findVersionWithModpack(id: UUID): Modpack2VersionOwner? =
        ModpackTable
            .innerJoin(ModpackVersionTable, { ModpackTable.id }, { ModpackVersionTable.modpackId })
            .selectAll()
            .where { ModpackVersionTable.id eq id }
            .singleOrNull()
            ?.let {
                Modpack2VersionOwner(
                    version = it.toModpackVersion(),
                    modpack = it.toModpack()
                )
            }

    fun findVersionWithModpackForUpdate(id: UUID): Modpack2VersionOwner? =
        ModpackTable
            .innerJoin(ModpackVersionTable, { ModpackTable.id }, { ModpackVersionTable.modpackId })
            .selectAll()
            .where { ModpackVersionTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.let {
                Modpack2VersionOwner(
                    version = it.toModpackVersion(),
                    modpack = it.toModpack()
                )
            }

    fun findCurrentVersion(modpackId: UUID): Modpack2Version? =
        ModpackVersionTable.selectAll()
            .where {
                (ModpackVersionTable.modpackId eq modpackId) and
                    (ModpackVersionTable.status eq Modpack2VersionStatus.Ok.name)
            }
            .orderBy(ModpackVersionTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toModpackVersion()

    fun listVersions(modpackId: UUID): List<Modpack2Version> =
        ModpackVersionTable.selectAll()
            .where { ModpackVersionTable.modpackId eq modpackId }
            .orderBy(ModpackVersionTable.id, SortOrder.DESC)
            .map { it.toModpackVersion() }

    fun updateVersionNameAndChangelog(
        id: UUID,
        name: String,
        changelog: String,
    ): Boolean = ModpackVersionTable.update({ ModpackVersionTable.id eq id }) {
        it[ModpackVersionTable.name] = name.trim()
        it[ModpackVersionTable.changelog] = changelog
    } == 1

    fun updateChangelog(id: UUID, changelog: String): Boolean =
        ModpackVersionTable.update({ ModpackVersionTable.id eq id }) {
            it[ModpackVersionTable.changelog] = changelog
        } == 1

    fun transitionFailToBuilding(id: UUID): Boolean =
        ModpackVersionTable.update({
            (ModpackVersionTable.id eq id) and
                (ModpackVersionTable.status eq Modpack2VersionStatus.Fail.name)
        }) {
            it[ModpackVersionTable.status] = Modpack2VersionStatus.Building.name
            it[ModpackVersionTable.totalSize] = 0
        } == 1

    fun transitionBuildingToOk(id: UUID, totalSize: Long): Boolean {
        require(totalSize >= 0) { "version total size must be non-negative" }
        return ModpackVersionTable.update({
            (ModpackVersionTable.id eq id) and
                (ModpackVersionTable.status eq Modpack2VersionStatus.Building.name)
        }) {
            it[ModpackVersionTable.status] = Modpack2VersionStatus.Ok.name
            it[ModpackVersionTable.totalSize] = totalSize
        } == 1
    }

    fun transitionBuildingToFail(id: UUID): Boolean =
        ModpackVersionTable.update({
            (ModpackVersionTable.id eq id) and
                (ModpackVersionTable.status eq Modpack2VersionStatus.Building.name)
        }) {
            it[ModpackVersionTable.status] = Modpack2VersionStatus.Fail.name
            it[ModpackVersionTable.totalSize] = 0
        } == 1

    /**
     * Marks all rows left in Building after a restart as Fail. The returned
     * owner information is intentionally small so the service can send mail
     * after committing the transaction.
     */
    fun markAllBuildingFailed(): List<Modpack2BuildingFailure> {
        val rows = ModpackTable
            .innerJoin(ModpackVersionTable, { ModpackTable.id }, { ModpackVersionTable.modpackId })
            .selectAll()
            .where { ModpackVersionTable.status eq Modpack2VersionStatus.Building.name }
            .forUpdate()
            .map {
                Modpack2BuildingFailure(
                    versionId = it[ModpackVersionTable.id],
                    modpackId = it[ModpackVersionTable.modpackId],
                    ownerId = it[ModpackTable.ownerId],
                    uploaderId = it[ModpackVersionTable.uploaderId],
                )
            }
        rows.forEach { row ->
            ModpackVersionTable.update({
                (ModpackVersionTable.id eq row.versionId) and
                    (ModpackVersionTable.status eq Modpack2VersionStatus.Building.name)
            }) {
                it[ModpackVersionTable.status] = Modpack2VersionStatus.Fail.name
                it[ModpackVersionTable.totalSize] = 0
            }
        }
        return rows
    }

    fun deleteVersion(id: UUID): Boolean =
        ModpackVersionTable.deleteWhere { ModpackVersionTable.id eq id } == 1

    fun findUploadResult(
        uploadId: UUID,
        ownerId: UUID,
        now: Long,
        requestModpackId: UUID? = null,
    ): Modpack2UploadResult? =
        ModpackUploadResultTable.selectAll()
            .where {
                val base = (ModpackUploadResultTable.uploadId eq uploadId) and
                    (ModpackUploadResultTable.ownerId eq ownerId) and
                    (ModpackUploadResultTable.expiresAt greater now)
                requestModpackId?.let { base and (ModpackUploadResultTable.requestModpackId eq it) }
                    ?: base
            }
            .singleOrNull()
            ?.toModpackUploadResult()

    fun insertUploadResult(result: Modpack2UploadResult): Boolean =
        ModpackUploadResultTable.insertIgnore {
            it[uploadId] = result.uploadId
            it[ownerId] = result.ownerId
            it[modpackId] = result.modpackId
            it[versionId] = result.versionId
            it[expiresAt] = result.expiresAt
            it[requestModpackId] = result.requestModpackId
        }.insertedCount == 1

    fun deleteUploadResult(uploadId: UUID): Boolean =
        ModpackUploadResultTable.deleteWhere {
            ModpackUploadResultTable.uploadId eq uploadId
        } == 1

    fun deleteExpiredUploadResults(now: Long): Int =
        ModpackUploadResultTable.deleteWhere {
            ModpackUploadResultTable.expiresAt lessEq now
        }

    fun listContents(versionId: UUID): List<Modpack2Content> =
        ModpackContentTable.selectAll()
            .where { ModpackContentTable.versionId eq versionId }
            .orderBy(
                ModpackContentTable.platform to SortOrder.ASC,
                ModpackContentTable.type to SortOrder.ASC,
                ModpackContentTable.projectId to SortOrder.ASC,
            )
            .map { it.toModpackContent() }

    fun listRawFiles(versionId: UUID): List<Modpack2RawFile> =
        ModpackRawFileTable.selectAll()
            .where { ModpackRawFileTable.versionId eq versionId }
            .orderBy(
                ModpackRawFileTable.root to SortOrder.ASC,
                ModpackRawFileTable.path to SortOrder.ASC,
            )
            .map { it.toModpackRawFile() }

    fun replaceRawFiles(versionId: UUID, files: Iterable<Modpack2RawFile>) {
        ModpackRawFileTable.deleteWhere { ModpackRawFileTable.versionId eq versionId }
        files.forEach { file ->
            ModpackRawFileTable.insert {
                it[ModpackRawFileTable.versionId] = versionId
                it[ModpackRawFileTable.root] = file.root.name.lowercase()
                it[ModpackRawFileTable.path] = file.path
                it[ModpackRawFileTable.sha1] = file.sha1.lowercase()
                it[ModpackRawFileTable.fileSize] = file.size
            }
        }
    }

    fun replaceContents(versionId: UUID, contents: Iterable<Modpack2Content>) {
        ModpackContentTable.deleteWhere { ModpackContentTable.versionId eq versionId }
        contents.forEach { content ->
            ModpackContentTable.insert {
                it[ModpackContentTable.versionId] = versionId
                it[ModpackContentTable.platform] = content.platform.name
                it[ModpackContentTable.type] = content.type.name
                it[ModpackContentTable.projectId] = content.projectId
                it[ModpackContentTable.fileId] = content.fileId
                it[ModpackContentTable.slug] = content.slug
                it[ModpackContentTable.hash] = content.hash
                it[ModpackContentTable.targetPath] = content.targetPath
                it[ModpackContentTable.side] = content.side.name
                it[ModpackContentTable.required] = content.required
                it[ModpackContentTable.fileSize] = content.fileSize
            }
        }
    }

    private fun normalizePage(offset: Long, limit: Int): PageWindow {
        require(offset >= 0) { "offset must be non-negative" }
        return PageWindow(offset = offset, limit = limit.coerceIn(1, MAX_PAGE_SIZE))
    }

    private fun pageResult(
        rows: List<Modpack2Detail>,
        total: Long,
        page: PageWindow,
    ): Modpack2Page<Modpack2Detail> {
        val remaining = (total - page.offset).coerceAtLeast(0)
        return Modpack2Page(
            items = rows,
            total = total,
            offset = page.offset,
            limit = page.limit,
            hasMore = rows.size.toLong() < remaining,
        )
    }

    private fun publicFilter(
        keyword: String?,
        category: ModpackCategory?,
        ownerId: UUID?,
    ): SqlFilter {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Pair<IColumnType<*>, Any?>>()
        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            clauses += "(m.name ILIKE ? OR m.intro ILIKE ?)"
            val pattern = "%$value%"
            args += textArgument(pattern)
            args += textArgument(pattern)
        }
        category?.let { value ->
            clauses += "EXISTS (SELECT 1 FROM modpack_category filter_category " +
                "WHERE filter_category.modpack_id = m.id " +
                "AND filter_category.category = ?)"
            args += textArgument(value.name)
        }
        ownerId?.let { value ->
            clauses += "m.owner_id = ?"
            args += uuidArgument(value)
        }
        return SqlFilter(
            sql = clauses.ifEmpty { listOf("TRUE") }.joinToString(" AND "),
            args = args,
        )
    }

    private fun publicVisibilitySql(): String =
        "EXISTS (SELECT 1 FROM modpack_version visible_version " +
            "WHERE visible_version.modpack_id = m.id " +
            "AND visible_version.status = 'Ok')"

    private fun queryDetails(
        where: String,
        args: List<Pair<IColumnType<*>, Any?>>,
        sort: Modpack2Sort,
        includeOnlyOk: Boolean,
        page: PageWindow,
        ownedOrder: Boolean = false,
    ): List<Modpack2Detail> {
        val currentVersionJoin = if (includeOnlyOk) "JOIN" else "LEFT JOIN"
        val orderBy = when {
            ownedOrder -> "m.id DESC"
            sort == Modpack2Sort.Popular -> "s.play_count DESC, m.id DESC"
            sort == Modpack2Sort.Name -> "m.name ASC, m.id DESC"
            else -> "v.id DESC NULLS LAST, m.id DESC"
        }
        val sql = """
            SELECT
                m.id AS modpack_id,
                m.name AS modpack_name,
                m.owner_id AS modpack_owner_id,
                m.intro AS modpack_intro,
                m.mc AS modpack_mc,
                m.loader AS modpack_loader,
                m.icon_url AS modpack_icon_url,
                m.source_url AS modpack_source_url,
                s.modpack_id AS stats_modpack_id,
                s.play_count AS stats_play_count,
                s.play_time_sec AS stats_play_time_sec,
                v.id AS version_id,
                v.modpack_id AS version_modpack_id,
                v.uploader_id AS version_uploader_id,
                v.name AS version_name,
                v.changelog AS version_changelog,
                v.status AS version_status,
                v.total_size AS version_total_size,
                v.server_mode AS version_server_mode,
                v.base_version_id AS version_base_version_id,
                (SELECT COUNT(*) FROM modpack_content content
                    WHERE content.version_id = v.id) AS content_count,
                COALESCE(
                    string_agg(DISTINCT category.category, ',' ORDER BY category.category),
                    ''
                ) AS categories
            FROM modpack m
            JOIN modpack_stats s ON s.modpack_id = m.id
            $currentVersionJoin LATERAL (
                SELECT current_version.*
                FROM modpack_version current_version
                WHERE current_version.modpack_id = m.id
                  AND current_version.status = 'Ok'
                ORDER BY current_version.id DESC
                LIMIT 1
            ) v ON TRUE
            LEFT JOIN modpack_category category ON category.modpack_id = m.id
            WHERE $where
            GROUP BY
                m.id, m.name, m.owner_id, m.intro, m.mc, m.loader,
                m.icon_url, m.source_url,
                s.modpack_id, s.play_count, s.play_time_sec,
                v.id, v.modpack_id, v.uploader_id, v.name,
                v.changelog, v.status, v.total_size,
                v.server_mode, v.base_version_id
            ORDER BY $orderBy
            LIMIT ${page.limit} OFFSET ${page.offset}
        """.trimIndent()
        return executeQuery(sql, args) { resultSet ->
            buildList {
                while (resultSet.next()) add(resultSet.toModpack2Detail())
            }
        }
    }

    private fun countDetails(
        where: String,
        args: List<Pair<IColumnType<*>, Any?>>,
    ): Long {
        val sql = "SELECT COUNT(*) FROM modpack m WHERE $where"
        return executeQuery(sql, args) { resultSet ->
            if (resultSet.next()) resultSet.getLong(1) else 0L
        }
    }

    private fun <T : Any> executeQuery(
        sql: String,
        args: List<Pair<IColumnType<*>, Any?>>,
        transform: (ResultSet) -> T,
    ): T = requireNotNull(TransactionManager.current().exec(sql, args, transform = transform))

    private fun insertDefaultStats(modpackId: UUID) {
        ModpackStatsTable.insert {
            it[ModpackStatsTable.modpackId] = modpackId
            it[ModpackStatsTable.playCount] = 0
            it[ModpackStatsTable.playTimeSec] = 0
        }
    }

    private data class PageWindow(
        val offset: Long,
        val limit: Int,
    )

    private data class SqlFilter(
        val sql: String,
        val args: List<Pair<IColumnType<*>, Any?>>,
    )
}

private const val MAX_PAGE_SIZE = 100

private fun textArgument(value: String): Pair<IColumnType<*>, Any?> = TextColumnType() to value

private fun uuidArgument(value: UUID): Pair<IColumnType<*>, Any?> = UUIDColumnType() to value

private fun ResultSet.toModpack2Detail(): Modpack2Detail {
    val modpackId = getObject("modpack_id") as UUID
    val versionId = getObject("version_id") as UUID?
    val categories = getString("categories")
        .orEmpty()
        .split(',')
        .filter(String::isNotEmpty)
        .map { category ->
            Modpack2Category(
                modpackId = modpackId,
                category = ModpackCategory.valueOf(category),
            )
        }
    val currentVersion = versionId?.let {
        Modpack2Version(
            id = it,
            modpackId = getObject("version_modpack_id") as UUID,
            uploaderId = getObject("version_uploader_id") as UUID?,
            name = getString("version_name"),
            changelog = getString("version_changelog"),
            status = Modpack2VersionStatus.valueOf(getString("version_status")),
            totalSize = getLong("version_total_size"),
            serverMode = Modpack2ServerMode.valueOf(getString("version_server_mode")),
            baseVersionId = getObject("version_base_version_id") as UUID?,
        )
    }
    return Modpack2Detail(
        modpack = Modpack2(
            id = modpackId,
            name = getString("modpack_name"),
            ownerId = getObject("modpack_owner_id") as UUID,
            intro = getString("modpack_intro"),
            mc = getInt("modpack_mc"),
            loader = Modpack2Loader.valueOf(getString("modpack_loader")),
            iconUrl = getString("modpack_icon_url"),
            sourceUrl = getString("modpack_source_url"),
        ),
        categories = categories,
        stats = Modpack2Stats(
            modpackId = getObject("stats_modpack_id") as UUID,
            playCount = getInt("stats_play_count"),
            playTimeSec = getLong("stats_play_time_sec"),
        ),
        currentVersion = currentVersion,
        contentCount = getLong("content_count"),
    )
}

data class Modpack2BuildingFailure(
    val versionId: UUID,
    val modpackId: UUID,
    val ownerId: UUID,
    val uploaderId: UUID?,
)

private object ModpackTable : Table("modpack") {
    val id = javaUUID("id").databaseGenerated()
    val name = text("name")
    val ownerId = javaUUID("owner_id")
    val intro = text("intro")
    val mc = short("mc")
    val loader = text("loader")
    val iconUrl = text("icon_url")
    val sourceUrl = text("source_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object ModpackCategoryTable : Table("modpack_category") {
    val modpackId = javaUUID("modpack_id")
    val category = text("category")

    override val primaryKey = PrimaryKey(modpackId, category)
}

private object ModpackStatsTable : Table("modpack_stats") {
    val modpackId = javaUUID("modpack_id")
    val playCount = integer("play_count")
    val playTimeSec = long("play_time_sec")

    override val primaryKey = PrimaryKey(modpackId)
}

private object ModpackVersionTable : Table("modpack_version") {
    val id = javaUUID("id").databaseGenerated()
    val modpackId = javaUUID("modpack_id")
    val uploaderId = javaUUID("uploader_id").nullable()
    val name = text("name")
    val changelog = text("changelog")
    val status = text("status")
    val totalSize = long("total_size")
    val serverMode = text("server_mode")
    val baseVersionId = javaUUID("base_version_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object ModpackContentTable : Table("modpack_content") {
    val contentId = long("content_id").databaseGenerated()
    val versionId = javaUUID("version_id")
    val platform = text("platform")
    val type = text("type")
    val projectId = text("project_id")
    val fileId = text("file_id")
    val slug = text("slug")
    val hash = text("hash")
    val targetPath = text("target_path").nullable()
    val side = text("side")
    val required = bool("required")
    val fileSize = long("file_size")

    override val primaryKey = PrimaryKey(contentId)
}

private object ModpackUploadResultTable : Table("modpack_upload_result") {
    val uploadId = javaUUID("upload_id")
    val ownerId = javaUUID("owner_id")
    val modpackId = javaUUID("modpack_id")
    val versionId = javaUUID("version_id")
    val expiresAt = long("expires_at")
    val requestModpackId = javaUUID("request_modpack_id").nullable()

    override val primaryKey = PrimaryKey(uploadId)
}

private object ModpackRawFileTable : Table("modpack_raw_file") {
    val versionId = javaUUID("version_id")
    val root = text("root")
    val path = text("path")
    val sha1 = text("sha1")
    val fileSize = long("file_size")

    override val primaryKey = PrimaryKey(versionId, root, path)
}

private object ModpackVersionManifestTable : Table("modpack2_version_manifest") {
    val versionId = javaUUID("version_id")
    val format = text("format")
    val manifestJson = text("manifest_json")
    val bindingsJson = text("bindings_json")

    override val primaryKey = PrimaryKey(versionId)
}

private fun ResultRow.toModpack(): Modpack2 = Modpack2(
    id = this[ModpackTable.id],
    name = this[ModpackTable.name],
    ownerId = this[ModpackTable.ownerId],
    intro = this[ModpackTable.intro],
    mc = this[ModpackTable.mc].toInt(),
    loader = Modpack2Loader.valueOf(this[ModpackTable.loader]),
    iconUrl = this[ModpackTable.iconUrl],
    sourceUrl = this[ModpackTable.sourceUrl],
)

private fun ResultRow.toModpackCategory(): Modpack2Category = Modpack2Category(
    modpackId = this[ModpackCategoryTable.modpackId],
    category = ModpackCategory.valueOf(this[ModpackCategoryTable.category]),
)

private fun ResultRow.toModpackStats(): Modpack2Stats = Modpack2Stats(
    modpackId = this[ModpackStatsTable.modpackId],
    playCount = this[ModpackStatsTable.playCount],
    playTimeSec = this[ModpackStatsTable.playTimeSec],
)

private fun ResultRow.toModpackVersion(): Modpack2Version = Modpack2Version(
    id = this[ModpackVersionTable.id],
    modpackId = this[ModpackVersionTable.modpackId],
    uploaderId = this[ModpackVersionTable.uploaderId],
    name = this[ModpackVersionTable.name],
    changelog = this[ModpackVersionTable.changelog],
    status = Modpack2VersionStatus.valueOf(this[ModpackVersionTable.status]),
    totalSize = this[ModpackVersionTable.totalSize],
    serverMode = Modpack2ServerMode.valueOf(this[ModpackVersionTable.serverMode]),
    baseVersionId = this[ModpackVersionTable.baseVersionId],
)

private fun ResultRow.toModpackContent(): Modpack2Content = Modpack2Content(
    versionId = this[ModpackContentTable.versionId],
    platform = ContentPlatform.valueOf(this[ModpackContentTable.platform]),
    type = ContentType.valueOf(this[ModpackContentTable.type]),
    projectId = this[ModpackContentTable.projectId],
    fileId = this[ModpackContentTable.fileId],
    slug = this[ModpackContentTable.slug],
    hash = this[ModpackContentTable.hash],
    targetPath = this[ModpackContentTable.targetPath],
    side = ContentSide.valueOf(this[ModpackContentTable.side]),
    required = this[ModpackContentTable.required],
    fileSize = this[ModpackContentTable.fileSize],
)

private fun ResultRow.toModpackUploadResult(): Modpack2UploadResult = Modpack2UploadResult(
    uploadId = this[ModpackUploadResultTable.uploadId],
    ownerId = this[ModpackUploadResultTable.ownerId],
    modpackId = this[ModpackUploadResultTable.modpackId],
    versionId = this[ModpackUploadResultTable.versionId],
    expiresAt = this[ModpackUploadResultTable.expiresAt],
    requestModpackId = this[ModpackUploadResultTable.requestModpackId],
)

private fun ResultRow.toModpackRawFile(): Modpack2RawFile = Modpack2RawFile(
    root = Modpack2RawFileRoot.valueOf(this[ModpackRawFileTable.root].replaceFirstChar(Char::uppercaseChar)),
    path = this[ModpackRawFileTable.path],
    sha1 = this[ModpackRawFileTable.sha1],
    size = this[ModpackRawFileTable.fileSize],
)

data class Modpack2VersionOwner(
    val version: Modpack2Version,
    val modpack: Modpack2,
)

data class Modpack2UploadResult(
    val uploadId: UUID,
    val ownerId: UUID,
    val modpackId: UUID,
    val versionId: UUID,
    val expiresAt: Long,
    val requestModpackId: UUID? = null,
)
