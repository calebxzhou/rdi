package calebxzau.rdi.client.modcatalog.tools

import calebxzhou.rdi.common.model.McVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object WikiEntriesReader {
    private const val ALIAS_SEPARATOR = '¨'
    private const val BASE86_DIGITS =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/+=!?@#\$%^&*()[]{}<>;:',"

    fun read(path: Path): Result<List<WikiPage>> = runCatching {
        val lines = Files.readAllLines(path, Charsets.UTF_8)
        require(lines.size >= 2) { "WikiEntries.txt缺少数据" }
        val dataLines = lines.dropLast(1)
        val popularity = lines.last().chunked(3).map(::decodeBase86)
        require(popularity.size == dataLines.count(String::isNotEmpty)) { "WikiEntries热度数量不匹配" }
        var popularityIndex = 0
        buildList {
            dataLines.forEachIndexed { index, line ->
                if (line.isEmpty()) return@forEachIndexed
                add(
                    WikiPage(
                        mcmodId = index + 1,
                        popularity = popularity[popularityIndex++],
                        aliases = line.split(ALIAS_SEPARATOR).map(::parseAlias)
                    )
                )
            }
        }
    }

    private fun decodeBase86(value: String): Int = value.fold(0) { result, char ->
        val digit = BASE86_DIGITS.indexOf(char)
        require(digit >= 0) { "WikiEntries包含无效Base86字符" }
        result * 86 + digit
    }

    private fun parseAlias(raw: String): WikiAlias {
        val parts = raw.split('|')
        val token = parts.first()
        val curseForgeSlug: String?
        val modrinthSlug: String?
        when {
            token.startsWith('@') -> {
                curseForgeSlug = null
                modrinthSlug = token.removePrefix("@").ifBlank { null }
            }

            token.endsWith('@') -> {
                curseForgeSlug = token.dropLast(1).ifBlank { null }
                modrinthSlug = curseForgeSlug
            }

            '@' in token -> {
                val slugs = token.split('@', limit = 2)
                curseForgeSlug = slugs[0].ifBlank { null }
                modrinthSlug = slugs[1].ifBlank { null }
            }

            else -> {
                curseForgeSlug = token.ifBlank { null }
                modrinthSlug = null
            }
        }
        require(curseForgeSlug != null || modrinthSlug != null) { "Wiki entry alias缺少slug" }
        val nameCn = parts.last().takeIf { parts.size > 1 && it.isNotBlank() }?.replace(
            "*",
            " (${(curseForgeSlug ?: modrinthSlug).orEmpty().slugDisplayName()})"
        )
        return WikiAlias(curseForgeSlug, modrinthSlug, nameCn)
    }
}

internal class McmodSourceFetcher(
    private val httpClient: HttpClient,
    private val cacheDir: Path,
    private val json: Json
) {
    suspend fun fetchAllEnabledVersions(): Result<List<McmodItem>> = try {
        Files.createDirectories(cacheDir)
        val items = linkedMapOf<Int, McmodItem>()
        //只取现代版本
        McVersion.entries.filter(McVersion::isModern).forEach { version ->
            fetchVersion(version.mcVer).forEach { items.putIfAbsent(it.mcmodId, it) }
        }
        Result.success(items.values.toList())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        Result.failure(cause)
    }

    private suspend fun fetchVersion(mcVersion: String): List<McmodItem> {
        val first = loadPage(mcVersion, 1)
        val items = linkedMapOf<Int, McmodItem>()
        first.items.forEach { items.putIfAbsent(it.mcmodId, it) }
        for (page in 2..first.totalPages) {
            loadPage(mcVersion, page).items.forEach { items.putIfAbsent(it.mcmodId, it) }
        }
        return items.values.toList()
    }

    private suspend fun loadPage(mcVersion: String, page: Int): McmodPage {
        val jsonFile = cacheDir.resolve("${mcVersion}_$page.json")
        val htmlFile = cacheDir.resolve("${mcVersion}_$page.html")
        if (Files.isRegularFile(jsonFile) && Files.isRegularFile(htmlFile)) {
            val parsed = McmodPageParser.parse(Files.readString(htmlFile))
            return parsed.copy(items = json.decodeFromString(Files.readString(jsonFile)))
        }
        val html = if (Files.isRegularFile(htmlFile)) {
            Files.readString(htmlFile)
        } else {
            download(mcVersion, page).also { writeAtomically(htmlFile, it) }
        }
        return McmodPageParser.parse(html).also { parsed ->
            writeAtomically(jsonFile, json.encodeToString(parsed.items))
        }
    }

    private suspend fun download(mcVersion: String, page: Int): String {
        val url = URLBuilder("https://www.mcmod.cn/modlist.html").apply {
            parameters.append("mcver", mcVersion)
            parameters.append("platform", "1")
            parameters.append("sort", "createtime")
            if (page > 1) parameters.append("page", page.toString())
        }.buildString()
        delay(300)
        val response = httpClient.get(url) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) RDI5CatalogTool/1")
            accept(ContentType.Text.Html)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw IOException("MC百科请求失败: HTTP${response.status.value} $url")
        if (body.isBlank()) throw IOException("MC百科返回空页面: $url")
        return body
    }
}

internal object McmodPageParser {
    private val pageInfoRegex = Regex("当前\\s*(\\d+)\\s*/\\s*(\\d+)\\s*页[^0-9]*?(\\d+)\\s*条")
    private val classIdRegex = Regex("/class/(\\d+)\\.html")

    fun parse(html: String): McmodPage {
        val document = Jsoup.parse(html, "https://www.mcmod.cn/")
        val pageMatch = sequence {
            yieldAll(document.select(".page-info, .common-pagination, .pagination, .page").map(Element::text))
            yield(document.body().text())
        }.firstNotNullOfOrNull(pageInfoRegex::find) ?: error("无法解析MC百科分页信息")
        val blocks = document.select(".modlist-block")
        check(blocks.isNotEmpty()) { "MC百科页面没有modlist-block" }
        val items = blocks.mapNotNull { block ->
            val link = block.selectFirst("a[href^=/class/]") ?: return@mapNotNull null
            val id = classIdRegex.find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val image: Element? = block.selectFirst("img")
            val logo: String = image?.let {
                sequenceOf(it.absUrl("data-original"), it.absUrl("data-src"), it.absUrl("src"))
                    .firstOrNull(String::isNotBlank)
            }.orEmpty()
            val nameCn = block.selectFirst(".title > .name")?.text()?.trim().orEmpty()
            val name = block.selectFirst(".title > .ename")?.text()?.trim().orEmpty()
            McmodItem(
                mcmodId = id,
                logoUrl = logo,
                name = name.ifBlank { nameCn.ifBlank { "class-$id" } },
                nameCn = nameCn.takeIf { name.isNotBlank() && it.isNotBlank() },
                intro = block.selectFirst(".intro-content")?.text()?.trim().orEmpty()
            )
        }
        return McmodPage(
            currentPage = pageMatch.groupValues[1].toInt(),
            totalPages = pageMatch.groupValues[2].toInt(),
            totalItems = pageMatch.groupValues[3].toInt(),
            items = items
        )
    }
}

internal fun writeAtomically(path: Path, text: String) {
    Files.createDirectories(path.parent)
    val temporary = path.resolveSibling("${path.fileName}.tmp")
    Files.writeString(temporary, text)
    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

internal fun String.slugDisplayName(): String = replace('-', ' ').split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.titlecase() } }
