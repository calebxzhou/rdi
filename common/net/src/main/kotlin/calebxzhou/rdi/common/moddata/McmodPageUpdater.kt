package calebxzhou.rdi.common.moddata

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.service.McmodService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.net.URI
import kotlin.math.max

/**
 * calebxzhou @ 2025-10-14 14:40
 * upd 26.02.07
 * 从mc百科获取mod的中文名称 简介 图标等信息 保存到mcmod_mod_data.json
 */
internal val mcmodDataDir = File("mcmod-data").apply { mkdir() }
val lgr by Loggers
suspend fun main() {
    listOf("1.7.10", "1.12.2", "1.16.5", "1.18.2", "1.20.1", "1.21.1")
        .forEach { fetchAllPages("https://www.mcmod.cn/modlist.html?mcver=${it}&platform=1&sort=createtime") }
}

//mc百科的mod信息
@Serializable
data class McmodModBriefInfo(
    //页面id  /class/{id}.html
    val id: Int,
    val logoUrl: String,
    val name: String,
    //中文名
    val nameCn: String? = null,
    //一句话介绍
    val intro: String,
)

private val MCVER_REGEX = Regex("[?&]mcver=([^&]+)")

private fun extractMcVer(url: String): String {
    return MCVER_REGEX.find(url)?.groupValues?.get(1) ?: "unknown"
}

private fun getJsonFile(mcVer: String, page: Int): File {
    return File(mcmodDataDir, "${mcVer}_${page}.json")
}

private fun getHtmlFile(mcVer: String, page: Int): File {
    return File(mcmodDataDir, "${mcVer}_${page}.html")
}

private suspend fun fetchAllPages(baseUrl: String) {
    lgr.info { baseUrl }
    val mcVer = extractMcVer(baseUrl)

    // Phase 1: Download all HTML pages
    lgr.info { "=== 阶段1: 下载HTML ===" }
    val totalPages = downloadAllHtmlPages(baseUrl, mcVer)

    // Phase 2: Parse all HTML to JSON
    lgr.info { "=== 阶段2: 解析HTML到JSON ===" }
    parseAllHtmlToJson(mcVer, totalPages)

    lgr.info { "$mcVer 完成" }
}

private suspend fun downloadAllHtmlPages(baseUrl: String, mcVer: String): Int {
    // Check if first page JSON exists to skip, otherwise download HTML
    val firstJsonFile = getJsonFile(mcVer, 1)
    val firstHtmlFile = getHtmlFile(mcVer, 1)

    var totalPages = 1

    // Need to fetch first page to get total pages count (unless we already have all JSONs)
    if (firstJsonFile.exists()) {
        lgr.info { "跳过已存在: ${firstJsonFile.name}" }
        // Try to infer total pages from existing files
        val existingJsonFiles = mcmodDataDir.listFiles { f ->
            f.name.startsWith("${mcVer}_") && f.name.endsWith(".json")
        } ?: emptyArray()
        totalPages = existingJsonFiles.mapNotNull {
            it.nameWithoutExtension.substringAfter("${mcVer}_").toIntOrNull()
        }.maxOrNull() ?: 1

        // Still need to check actual total from server
        val firstHtml = downloadPageHtml(buildPageUrl(baseUrl, null))
        if (firstHtml != null) {
            val doc = Jsoup.parse(firstHtml, baseUrl)
            val pageInfo = parsePageInfo(doc)
            totalPages = max(pageInfo.totalPages, totalPages)
        }
    } else if (firstHtmlFile.exists()) {
        lgr.info { "HTML已存在: ${firstHtmlFile.name}" }
        val doc = Jsoup.parse(firstHtmlFile.readText(), baseUrl)
        val pageInfo = parsePageInfo(doc)
        totalPages = max(pageInfo.totalPages, pageInfo.currentPage)
    } else {
        val firstHtml = downloadPageHtml(buildPageUrl(baseUrl, null)) ?: return 0
        firstHtmlFile.writeText(firstHtml)
        lgr.info { "已下载: ${firstHtmlFile.name}" }

        val doc = Jsoup.parse(firstHtml, baseUrl)
        val pageInfo = parsePageInfo(doc)
        totalPages = max(pageInfo.totalPages, pageInfo.currentPage)
    }

    lgr.info { "共 ${totalPages} 页" }

    // Download remaining pages sequentially with 100ms delay
    for (page in 2..totalPages) {
        val jsonFile = getJsonFile(mcVer, page)
        val htmlFile = getHtmlFile(mcVer, page)
        
        if (jsonFile.exists()) {
            lgr.info { "跳过已存在: ${jsonFile.name}" }
            continue
        }
        
        if (htmlFile.exists()) {
            lgr.info { "HTML已存在: ${htmlFile.name}" }
            continue
        }
        
        val pageUrl = buildPageUrl(baseUrl, page)
        val html = downloadPageHtml(pageUrl) ?: continue
        htmlFile.writeText(html)
        lgr.info { "已下载: ${htmlFile.name} ($page/$totalPages)" }
        
        delay(100)
    }
    
    return totalPages
}

private fun parseAllHtmlToJson(mcVer: String, totalPages: Int) {
    for (page in 1..totalPages) {
        val jsonFile = getJsonFile(mcVer, page)
        val htmlFile = getHtmlFile(mcVer, page)

        if (jsonFile.exists()) {
            lgr.info { "跳过已存在: ${jsonFile.name}" }
            continue
        }

        if (!htmlFile.exists()) {
            lgr.warn { "HTML文件不存在: ${htmlFile.name}" }
            continue
        }

        val doc = Jsoup.parse(htmlFile.readText(), "https://www.mcmod.cn/")
        val modInfos = extractModInfos(doc)
        jsonFile.writeText(modInfos.json)
        lgr.info { "已解析: ${htmlFile.name} -> ${jsonFile.name} (${modInfos.size}条)" }
    }
}

private fun buildPageUrl(baseUrl: String, page: Int?): String {
    return when {
        page == null || page <= 1 -> baseUrl
        baseUrl.contains("page=", ignoreCase = true) -> {
            baseUrl.replace(Regex("([?&])page=\\d+")) { match ->
                "${match.groupValues[1]}page=$page"
            }
        }

        baseUrl.contains('?') -> "$baseUrl&page=$page"
        else -> "$baseUrl?page=$page"
    }
}

private suspend fun downloadPageHtml(url: String, maxRetries: Int = 10): String? {
    val headers = headersForUrl(url)
    var retryCount = 0

    while (true) {
        val response = ktorClient.let { client ->
            client.request {
                url(url)
                method = HttpMethod.Get
                headers.forEach { (name, value) -> header(name, value) }
            }
        }


        if (response.status.value == 429) {
            retryCount++
            if (retryCount > maxRetries) {
                lgr.warn { "请求mcmod列表页失败: HTTP 429 重试次数已达上限 url=$url" }
                return null
            }
            val waitTime = 500L * retryCount // Backoff: 1s, 2s, 3s...
            lgr.warn { "HTTP 429 限流，等待 ${waitTime}ms 后重试 ($retryCount/$maxRetries)" }
            delay(waitTime)
            continue
        }

        if (response.status.value !in 200..299) {
            lgr.warn { "请求mcmod列表页失败: HTTP ${response.status.value} url=$url" }
            return null
        }

        val body = response.bodyAsText()
        if (body.isBlank()) {
            lgr.warn { "mcmod列表页返回空内容，url=$url" }
            return null
        }
        return body
    }
}

private fun headersForUrl(url: String): List<Pair<String, String>> {
    val host = runCatching { URI(url).host }.getOrNull()
    return if (host.isNullOrBlank()) {
        McmodService.mcmodHeader
    } else {
        McmodService.mcmodHeader.map { (key, value) ->
            if (key.equals("Host", ignoreCase = true)) key to host else key to value
        }
    }
}

private data class PageInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int
)

private val PAGE_INFO_REGEX = Regex("当前\\s*(\\d+)\\s*/\\s*(\\d+)\\s*页[^0-9]*?(\\d+)\\s*条")

private fun parsePageInfo(document: Document): PageInfo {
    val candidateText = sequence {
        yieldAll(document.select(".page-info, .common-pagination, .pagination, .page").map(Element::text))
        yield(document.body().text())
    }.firstNotNullOfOrNull { text ->
        PAGE_INFO_REGEX.find(text)?.let { match ->
            PageInfo(
                currentPage = match.groupValues[1].toInt(),
                totalPages = match.groupValues[2].toInt(),
                totalItems = match.groupValues[3].toInt()
            )
        }
    }

    return candidateText ?: throw IllegalStateException("无法解析分页信息")
}

private val CLASS_ID_REGEX = Regex("/class/(\\d+).html")

private fun extractModInfos(document: Document): List<McmodModBriefInfo> {
    val result = arrayListOf<McmodModBriefInfo>()

    val blocks = document.select(".modlist-block")
    if (blocks.isEmpty()) {
        lgr.warn { "该页面没有找到modlist-block" }
    }

    for (block in blocks) {
        val link = block.selectFirst("a[href^=/class/]") ?: continue
        val href = link.attr("href").trim()
        val pageId = CLASS_ID_REGEX.find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (pageId == null) {
            lgr.warn { "无法从链接解析pageId: $href" }
            continue
        }

        val logoUrl = block.selectFirst("img")?.let { img ->
            val dataOriginal = img.absUrl("data-original")
            val dataSrc = img.absUrl("data-src")
            val src = img.absUrl("src")
            sequenceOf(dataOriginal, dataSrc, src)
                .firstOrNull { it.isNotBlank() }
                ?: src
        }.orEmpty()

        val intro = block.selectFirst(".intro-content")?.text()?.trim().orEmpty()

        val rawNameCn = block.selectFirst(".title > .name")?.text()?.trim().orEmpty()
        val rawNameEn = block.selectFirst(".title > .ename")?.text()?.trim().orEmpty()

        val name: String
        val nameCn: String?

        if (rawNameEn.isNotBlank()) {
            name = rawNameEn
            nameCn = rawNameCn.ifBlank { null }
        } else {
            name = rawNameCn.ifBlank { "class-$pageId" }
            nameCn = null
        }

        val info = McmodModBriefInfo(
            id = pageId,
            logoUrl = logoUrl,
            name = name,
            nameCn = nameCn,
            intro = intro
        )
        lgr.info { "$name $nameCn $pageId" }
        result += info
    }

    lgr.info { "页面解析完成，共 ${result.size} 个mod" }
    return result
}
