package calebxzhou.rdi.common.ai

import calebxzhou.rdi.common.net.DynamicProxySelector
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Protocol
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object McmodItemLookupAiTool : AiTool {
    override val name = "mcmod_item_lookup"
    private const val MAX_CANDIDATES = 5
    private const val MAX_SUMMARY_CHARS = 700
    private const val MAX_DETAIL_CHARS = 8_000
    private const val MAX_OUTPUT_CHARS = 24_000
    private val itemUrlRegex = Regex("""/item/(\d+)\.html""")

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    protocols(listOf(Protocol.HTTP_1_1))
                    followRedirects(true)
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                    proxySelector(DynamicProxySelector())
                }
            }
            BrowserUserAgent()
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
                identity()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    override fun definition(context: AiToolContext) = AiToolDefinition(
        name = name,
        description = "Search MC百科 for a Minecraft item/block/entity by resource location or item name, then return candidate item pages and readable page details. Use this when a player asks what an item is, how to use it, where it comes from, or what a resource location means. If multiple candidates are returned, choose the one that best matches the player's request and current modpack context.",
        parameters = AiToolParameters(
            properties = mapOf(
                "resourceLocation" to AiToolProperty(type = "string", description = "Item/block/entity resource location or name, e.g. minecraft:diamond or thermal:machine_frame"),
                "question" to AiToolProperty(type = "string", description = "Optional original player question for context"),
                "limit" to AiToolProperty(type = "integer", description = "Candidate/detail limit, default 5, max 5")
            ),
            required = listOf("resourceLocation")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<McmodItemLookupArgs>(argumentsJson) ?: return argError(name)
        val query = args.resourceLocation.trim()
        if (query.isBlank()) {
            return AiToolExecution(result = AiToolResult(tool = name, error = "未提供要查询的物品注册名"))
        }
        val limit = args.limit.coerceIn(1, MAX_CANDIDATES)
        val searchUrl = mcmodSearchUrl(query)
        val access = AiToolAccess("MC百科搜索:$query", "搜索")
        val result = runCatching {
            val searchPage = fetchHtml(searchUrl)
            val candidates = parseSearchCandidates(searchPage.doc, limit)
            val details = candidates.map { candidate ->
                runCatching { parseItemDetail(candidate, fetchHtml(candidate.url).doc) }
                    .getOrElse {
                        McmodItemDetail(
                            pageId = candidate.pageId,
                            title = candidate.title,
                            url = candidate.url,
                            registryName = candidate.registryName,
                            error = it.message ?: "读取MC百科详情失败"
                        )
                    }
            }
            val payload = McmodItemLookupResult(
                query = query,
                question = args.question.trim().takeIf(String::isNotBlank),
                searchUrl = searchUrl,
                candidates = candidates,
                details = details,
                note = if (candidates.size > 1) {
                    "多个候选都可能相关，请结合玩家问题、资源名和整合包上下文选择最匹配的资料。"
                } else {
                    null
                }
            )
            val output = serdesJson.encodeToString(payload)
            AiToolResult(
                tool = name,
                target = searchUrl,
                status = searchPage.status,
                contentType = "application/json",
                output = output.take(MAX_OUTPUT_CHARS),
                outputBytes = output.toByteArray().size,
                resultCount = candidates.size,
                truncated = output.length > MAX_OUTPUT_CHARS
            )
        }.getOrElse {
            AiToolResult(tool = name, target = searchUrl, error = it.message ?: "MC百科查询失败")
        }
        return AiToolExecution(
            access = access,
            result = result,
            detail = AiToolDetail(
                method = "GET",
                path = URI(searchUrl).rawPath + "?key=...",
                response = result.output ?: result.error.orEmpty(),
                status = result.status
            )
        )
    }

    private suspend fun fetchHtml(url: String): McmodHtmlPage {
        val response = client.request {
            this.method = HttpMethod.Get
            url(url)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "zh-CN,zh;q=0.9")
            header(HttpHeaders.CacheControl, "no-cache")
        }
        val text = response.bodyAsText()
        if (text.contains("document.cookie") && text.contains("yxd_token")) {
            throw IllegalStateException("MC百科要求浏览器验证，暂时无法读取页面")
        }
        return McmodHtmlPage(
            status = response.status.value,
            doc = Jsoup.parse(text, url)
        )
    }

    private fun parseSearchCandidates(doc: Document, limit: Int): List<McmodSearchBrief> {
        return doc.select(".search-result-list .result-item")
            .asSequence()
            .mapNotNull { item ->
                val link = item.selectFirst(".head a[href*=item/]")
                    ?: item.selectFirst("a[href*=item/]")
                    ?: return@mapNotNull null
                val url = link.absUrl("href").ifBlank { link.attr("href").toMcmodAbsoluteUrl() }
                val pageId = itemUrlRegex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                val headText = item.selectFirst(".head")?.wholeText()?.normalizeText().orEmpty()
                val title = link.wholeText().normalizeText()
                val body = item.selectFirst(".body")?.wholeText()?.normalizeText().orEmpty()
                McmodSearchBrief(
                    title = title,
                    pageId = pageId,
                    url = "https://www.mcmod.cn/item/$pageId.html",
                    summary = body.take(MAX_SUMMARY_CHARS),
                    category = headText.substringBefore(title).trim().trim('(', ')').takeIf(String::isNotBlank),
                    modName = title.extractBracketModName(),
                    registryName = body.extractRegistryName()
                )
            }
            .distinctBy { it.pageId }
            .take(limit)
            .toList()
    }

    private fun parseItemDetail(candidate: McmodSearchBrief, doc: Document): McmodItemDetail {
        val pageId = candidate.pageId
        val title = doc.selectFirst(".itemname h5")?.wholeText()?.normalizeText()
            ?: doc.title().substringBefore(" - MC百科").normalizeText()
        val modName = doc.select(".common-nav a.item")
            .map { it.wholeText().normalizeText() }
            .firstOrNull { it.startsWith("[") && it.contains("]") }
            ?: doc.title().extractBracketModName()
        val infoRows = doc.select(".item-info-table table tr")
            .mapNotNull { row ->
                val cells = row.select("td").map { it.wholeText().normalizeText() }.filter(String::isNotBlank)
                if (cells.size >= 2) "${cells[0]} ${cells.drop(1).joinToString(" ")}" else null
            }
            .distinct()
            .take(24)
        val content = doc.selectFirst(".item-content.common-text")?.wholeText()?.normalizeText()
            ?: doc.selectFirst(".item-content")?.wholeText()?.normalizeText()
            ?: ""
        val recipeText = doc.select(".item-table-block .text, .item-table-tips")
            .map { it.wholeText().normalizeText() }
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
        val mergedContent = buildString {
            if (content.isNotBlank()) appendLine(content)
            if (infoRows.isNotEmpty()) {
                appendLine()
                appendLine("基础信息:")
                infoRows.forEach { appendLine("- $it") }
            }
            if (recipeText.isNotEmpty()) {
                appendLine()
                appendLine("合成/用途摘录:")
                recipeText.forEach { appendLine("- $it") }
            }
        }.trim()
        return McmodItemDetail(
            pageId = pageId,
            title = title,
            url = "https://www.mcmod.cn/item/$pageId.html",
            modName = modName,
            registryName = infoRows.firstNotNullOfOrNull { it.extractRegistryName() } ?: candidate.registryName,
            content = mergedContent.take(MAX_DETAIL_CHARS),
            truncated = mergedContent.length > MAX_DETAIL_CHARS
        )
    }

    private fun mcmodSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name()).replace("+", "%20")
        return "https://search.mcmod.cn/s?key=$encoded&site=&filter=0&mold=0"
    }

    private fun String.toMcmodAbsoluteUrl(): String {
        return when {
            startsWith("https://") || startsWith("http://") -> this
            startsWith("//") -> "https:$this"
            startsWith("/") -> "https://www.mcmod.cn$this"
            else -> "https://www.mcmod.cn/$this"
        }
    }

    private fun String.normalizeText(): String =
        replace('\u00a0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.extractBracketModName(): String? =
        Regex("""- \[([^\]]+)]\s*([^-\n]+)?""").find(this)
            ?.value
            ?.removePrefix("- ")
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun String.extractRegistryName(): String? =
        Regex("""注册名[:：]\s*([A-Za-z0-9_.:-]+)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
}

private data class McmodHtmlPage(
    val status: Int,
    val doc: Document
)

@Serializable
private data class McmodItemLookupArgs(
    val resourceLocation: String,
    val question: String = "",
    val limit: Int = 5
)

@Serializable
private data class McmodItemLookupResult(
    val query: String,
    val question: String? = null,
    val searchUrl: String,
    val candidates: List<McmodSearchBrief>,
    val details: List<McmodItemDetail>,
    val note: String? = null
)

@Serializable
private data class McmodSearchBrief(
    val title: String,
    val pageId: Int,
    val url: String,
    val summary: String,
    val category: String? = null,
    val modName: String? = null,
    val registryName: String? = null
)

@Serializable
private data class McmodItemDetail(
    val pageId: Int,
    val title: String,
    val url: String,
    val modName: String? = null,
    val registryName: String? = null,
    val content: String = "",
    val truncated: Boolean = false,
    val error: String? = null
)
