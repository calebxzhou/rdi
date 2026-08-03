package calebxzau.rdi.client.modcatalog.tools

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import calebxzau.rdi.client.modcatalog.ModPlatform
import calebxzhou.rdi.client.modcatalog.database.ModCatalogDatabase
import com.aeb.pinyin.api.NonZhFormatOption
import com.aeb.pinyin.api.PinyinFormatOptions
import com.aeb.pinyin.api.PinyinPro4J
import com.aeb.pinyin.api.PinyinToneMode
import com.aeb.pinyin.api.SegmentationOptions
import com.aeb.pinyin.api.StandardDictionary
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Instant
import java.util.HexFormat
import java.util.Properties

internal data class CatalogBuildSummary(
    val skipped: Boolean,
    val mods: Long,
    val projects: Long,
    val searchTerms: Long,
    val output: Path
)

internal class ModCatalogTool(
    private val workingDir: Path = Path.of("run"),
    private val outputFile: Path = Path.of("../mod-catalog/src/main/resources/mod_catalog.db")
) {
    private val json = Json { prettyPrint = true }

    suspend fun run(): Result<CatalogBuildSummary> = try {
        Files.createDirectories(workingDir)
        val wikiPages = WikiEntriesReader.read(workingDir.resolve("WikiEntries.txt")).getOrThrow()
        val mcmodItems = HttpClient(OkHttp).use { client ->
            McmodSourceFetcher(client, workingDir.resolve("mcmod-cache"), json)
                .fetchAllEnabledVersions()
                .getOrThrow()
        }
        val normalized = merge(wikiPages, mcmodItems)
        val normalizedJson = json.encodeToString(normalized)
        writeAtomically(workingDir.resolve("mod_catalog.json"), normalizedJson)
        val sourceSha1 = normalizedJson.toByteArray().sha1()
        val existing = readExistingMetadata(outputFile)?.takeIf {
            it.formatVersion == FORMAT_VERSION && it.sourceSha1 == sourceSha1 && it.countsMatch
        }
        Result.success(
            existing?.let {
                CatalogBuildSummary(true, it.modCount, it.projectCount, it.searchTermCount, outputFile)
            } ?: buildDatabase(normalized, sourceSha1)
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Result.failure(cause)
    }

    private fun merge(wikiPages: List<WikiPage>, mcmodItems: List<McmodItem>): List<CatalogToolMod> {
        val mcmodById = mcmodItems.groupBy(McmodItem::mcmodId)
        return wikiPages.map { page ->
            val remote = mcmodById[page.mcmodId].orEmpty()
            val nameCn = remote.firstNotNullOfOrNull { it.nameCn.normalized() }
                ?: page.aliases.firstNotNullOfOrNull { it.nameCn.normalized() }
            val projects = page.aliases.flatMapIndexed { order, alias ->
                buildList {
                    alias.curseForgeSlug.normalized()?.let {
                        add(CatalogToolProject(order, ModPlatform.CURSEFORGE, it, alias.nameCn.overrideFor(nameCn)))
                    }
                    alias.modrinthSlug.normalized()?.let {
                        add(CatalogToolProject(order, ModPlatform.MODRINTH, it, alias.nameCn.overrideFor(nameCn)))
                    }
                }
            }.distinctBy { it.platform to normalizeSlug(it.slug) }
                .mapIndexed { index, project -> project.copy(order = index) }
            require(projects.isNotEmpty()) { "mcmodId=${page.mcmodId}没有project mapping" }
            CatalogToolMod(
                mcmodId = page.mcmodId,
                popularity = page.popularity,
                name = remote.firstNotNullOfOrNull { it.name.normalized()?.takeUnless { name -> name == "class-${page.mcmodId}" } }
                    ?: projects.first().slug.slugDisplayName(),
                nameCn = nameCn,
                intro = remote.firstNotNullOfOrNull { it.intro.normalized() },
                logoUrl = remote.firstNotNullOfOrNull { it.logoUrl.normalized() },
                projects = projects
            )
        }.also(::validate)
    }

    private fun buildDatabase(mods: List<CatalogToolMod>, sourceSha1: String): CatalogBuildSummary {
        Files.createDirectories(outputFile.toAbsolutePath().normalize().parent)
        val temporary = outputFile.resolveSibling("${outputFile.fileName}.tmp")
        Files.deleteIfExists(temporary)
        val driver = JdbcSqliteDriver(
            "jdbc:sqlite:${temporary.toAbsolutePath().normalize()}",
            Properties().apply {
                setProperty("foreign_keys", "true")
                setProperty("journal_mode", "OFF")
                setProperty("synchronous", "OFF")
            }
        )
        var projectCount = 0L
        var searchTermCount = 0L
        try {
            ModCatalogDatabase.Schema.create(driver).value
            val database = ModCatalogDatabase(driver)
            val termGenerator = SearchTermGenerator()
            database.transaction {
                mods.sortedBy(CatalogToolMod::mcmodId).forEach { mod ->
                    database.modCatalogQueries.insertMod(
                        mod.mcmodId.toLong(),
                        mod.popularity.toLong(),
                        mod.name,
                        mod.nameCn,
                        mod.intro,
                        mod.logoUrl
                    )
                    mod.projects.forEach { project ->
                        database.modCatalogQueries.insertProject(
                            mod.mcmodId.toLong(),
                            project.order.toLong(),
                            project.platform.name,
                            project.slug,
                            normalizeSlug(project.slug),
                            project.nameCnOverride
                        )
                        projectCount++
                    }
                    termGenerator.generate(mod).toSortedMap().forEach { (term, weight) ->
                        database.modCatalogQueries.insertSearchTerm(mod.mcmodId.toLong(), term, weight.toLong())
                        searchTermCount++
                    }
                }
                database.modCatalogQueries.insertMetadata(
                    FORMAT_VERSION,
                    sourceSha1,
                    Instant.now().toString(),
                    mods.size.toLong(),
                    projectCount,
                    searchTermCount
                )
            }
        } catch (cause: Exception) {
            driver.close()
            Files.deleteIfExists(temporary)
            throw cause
        }
        driver.close()
        validateDatabase(temporary, mods.size.toLong(), projectCount, searchTermCount)
        Files.move(temporary, outputFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return CatalogBuildSummary(false, mods.size.toLong(), projectCount, searchTermCount, outputFile)
    }

    private fun validate(mods: List<CatalogToolMod>) {
        require(mods.map(CatalogToolMod::mcmodId).distinct().size == mods.size) { "mcmodId重复" }
        val slugs = hashSetOf<Pair<ModPlatform, String>>()
        mods.forEach { mod ->
            require(mod.mcmodId > 0 && mod.popularity >= 0 && mod.name.isNotBlank()) { "无效mod记录:${mod.mcmodId}" }
            mod.projects.forEach { project ->
                require(slugs.add(project.platform to normalizeSlug(project.slug))) {
                    "${project.platform} slug重复:${project.slug}"
                }
            }
        }
    }

    private fun validateDatabase(path: Path, mods: Long, projects: Long, terms: Long) {
        DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath().normalize()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA foreign_key_check").use { check(!it.next()) { "SQLite foreign key check失败" } }
                statement.executeQuery("PRAGMA integrity_check").use { check(it.next() && it.getString(1) == "ok") { "SQLite integrity check失败" } }
                statement.execute("ANALYZE")
            }
        }
        val metadata = requireNotNull(readExistingMetadata(path))
        check(metadata.modCount == mods && metadata.projectCount == projects && metadata.searchTermCount == terms)
    }

    private fun readExistingMetadata(path: Path): ExistingMetadata? {
        if (!Files.isRegularFile(path)) return null
        var driver: JdbcSqliteDriver? = null
        return try {
            driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath().normalize().toUri()}?mode=ro")
            val database = ModCatalogDatabase(driver)
            val stored = database.modCatalogQueries.selectMetadata().executeAsOne()
            ExistingMetadata(
                stored.format_version,
                stored.source_sha1,
                stored.mod_count,
                stored.project_count,
                stored.search_term_count,
                database.modCatalogQueries.countMods().executeAsOne(),
                database.modCatalogQueries.countProjects().executeAsOne(),
                database.modCatalogQueries.countSearchTerms().executeAsOne()
            )
        } catch (cause: Exception) {
            System.err.println("现有mod_catalog.db不可复用，将重新生成:${cause.message}")
            null
        } finally {
            driver?.close()
        }
    }

    private data class ExistingMetadata(
        val formatVersion: Long,
        val sourceSha1: String,
        val modCount: Long,
        val projectCount: Long,
        val searchTermCount: Long,
        val actualModCount: Long,
        val actualProjectCount: Long,
        val actualSearchTermCount: Long
    ) {
        val countsMatch: Boolean
            get() = Triple(modCount, projectCount, searchTermCount) ==
                Triple(actualModCount, actualProjectCount, actualSearchTermCount)
    }

    companion object {
        private const val FORMAT_VERSION = 1L
    }
}

private class SearchTermGenerator {
    private val context = PinyinPro4J.createNewContext(StandardDictionary.COMPLETE)
    private val segmentation = SegmentationOptions()
    private val format = PinyinFormatOptions()
        .setSegmentSeparator(" ")
        .setSyllableSeparator(" ")
        .setToneMode(PinyinToneMode.NONE_ASCII)
        .setNonZhOption(NonZhFormatOption.KEEP)

    fun generate(mod: CatalogToolMod): Map<String, Int> = buildMap {
        addTerm(mod.name, 90)
        mod.nameCn?.let { addChinese(it, 90) }
        mod.projects.forEach { project ->
            addTerm(project.slug, 100)
            project.nameCnOverride?.let { addChinese(it, 80) }
        }
    }

    private fun MutableMap<String, Int>.addChinese(value: String, weight: Int) {
        addTerm(value, weight)
        val syllables = PinyinPro4J.convertToPinyin(value, segmentation, format, context)
            .split(Regex("\\s+")).filter(String::isNotBlank)
        addTerm(syllables.joinToString(""), 70)
        addTerm(syllables.joinToString("") { it.first().toString() }, 60)
    }

    private fun MutableMap<String, Int>.addTerm(value: String, weight: Int) {
        val normalized = normalize(value)
        if (normalized.isNotEmpty() && weight > (this[normalized] ?: -1)) this[normalized] = weight
    }
}

private fun normalize(value: String): String = value.trim().lowercase()
    .filter(Char::isLetterOrDigit)

private fun normalizeSlug(value: String): String = value.trim().lowercase()

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.overrideFor(base: String?): String? = normalized()?.takeUnless { it == base }

private fun ByteArray.sha1(): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(this))

fun main() = runBlocking {
    val summary = ModCatalogTool().run().getOrThrow()
    println(
        "mod_catalog.db完成: skipped=${summary.skipped} mods=${summary.mods} " +
            "projects=${summary.projects} searchTerms=${summary.searchTerms} output=${summary.output}"
    )
}
