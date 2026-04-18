package calebxzhou.rdi.client.service

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * 从整合包来源站(source url)抓简介(intro)和图集(gallery)。
 */
data class ModpackSourceIntro(
    val sourceName: String,
    val canonicalUrl: String,
    val summary: String? = null,
    val bodyMarkdown: String = "",
    val galleryUrls: List<String> = emptyList()
) {
    val hasContent: Boolean
        get() = !summary.isNullOrBlank() || bodyMarkdown.isNotBlank() || galleryUrls.isNotEmpty()
}

suspend fun fetchModpackSourceIntro(sourceUrl: String): Result<ModpackSourceIntro> = runCatching {
    val trimmedUrl = sourceUrl.trim()
    when {
        trimmedUrl.contains("/res-id/", ignoreCase = true) && trimmedUrl.contains("xyebbs.com", ignoreCase = true) ->
            fetchXyeIntro(trimmedUrl)

        trimmedUrl.contains("/modpack/", ignoreCase = true) && trimmedUrl.contains("bbsmc.net", ignoreCase = true) ->
            fetchBbsmcIntro(trimmedUrl)

        else -> throw RequestError("暂不支持从该来源抓取简介")
    }
}

private suspend fun fetchXyeIntro(sourceUrl: String): ModpackSourceIntro {
    val slug = sourceUrl.extractSlugAfter("/res-id/")
        ?: throw RequestError("无法解析xyebbs来源链接")
    val identify = requestJsonElement("https://resource-api.xyeidc.com/client/resources/identify/$slug?includes=*")
    val data = identify.jsonObject["data"]?.jsonObjectOrNull
        ?: throw RequestError("xyebbs返回为空")
    val resourceId = data.long("id")
        ?: throw RequestError("xyebbs资源id为空")
    val gallery = requestJsonElement("https://resource-api.xyeidc.com/client/resources/$resourceId/galleries?page=1")
    val galleryUrls = gallery.jsonObject["data"]
        .jsonArrayOrNull
        .orEmpty()
        .mapNotNull { item ->
            item.jsonObjectOrNull
                ?.string("picUuid")
                ?.let { "https://resource-api.xyeidc.com/client/members/pics/$it" }
        }

    return ModpackSourceIntro(
        sourceName = "XYE",
        canonicalUrl = "https://www.xyebbs.com/res-id/$slug",
        summary = data.string("description"),
        bodyMarkdown = sanitizeMarkdownBody(data.string("text")),
        galleryUrls = galleryUrls
    )
}

private suspend fun fetchBbsmcIntro(sourceUrl: String): ModpackSourceIntro {
    val slug = sourceUrl.extractSlugAfter("/modpack/")
        ?: throw RequestError("无法解析bbsmc来源链接")
    val project = requestJsonElement("https://api.bbsmc.net/v2/project/$slug")
    val data = project.jsonObject
    val galleryUrls = data["gallery"]
        .jsonArrayOrNull
        .orEmpty()
        .mapNotNull { item ->
            item.jsonPrimitiveOrNull?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
                ?: item.jsonObjectOrNull?.string("url", "image", "src", "original")
        }
        .filter { it.startsWith("http", ignoreCase = true) }

    return ModpackSourceIntro(
        sourceName = "BBSMC",
        canonicalUrl = "https://bbsmc.net/modpack/$slug",
        summary = data.string("description"),
        bodyMarkdown = sanitizeMarkdownBody(data.string("body")),
        galleryUrls = galleryUrls
    )
}

private suspend fun requestJsonElement(requestUrl: String): JsonElement {
    val response = httpRequest {
        url(requestUrl)
        header(HttpHeaders.AcceptEncoding, "identity")
    }
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
        throw RequestError("请求简介失败: $body")
    }
    return runCatching { serdesJson.parseToJsonElement(body) }
        .getOrElse { throw RequestError("解析简介响应失败", it) }
}

private fun sanitizeMarkdownBody(text: String?): String = text
    .orEmpty()
    .replace(IFRAME_BLOCK_REGEX, "\n")
    .replace(BR_TAG_REGEX, "\n")
    .replace(P_END_REGEX, "\n\n")
    .replace(P_START_REGEX, "")
    .replace(HTML_TAG_REGEX, "")
    .replace("&nbsp;", " ")
    .replace("\r\n", "\n")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

private fun String.extractSlugAfter(marker: String): String? {
    val markerIndex = indexOf(marker, ignoreCase = true)
    if (markerIndex < 0) return null
    return substring(markerIndex + marker.length)
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore('/')
        .trim()
        .takeIf(String::isNotBlank)
}

private fun JsonObject.string(vararg keys: String): String? = keys
    .asSequence()
    .mapNotNull { key -> this[key].jsonPrimitiveOrNull?.contentOrNull?.trim() }
    .firstOrNull(String::isNotBlank)

private fun JsonObject.long(key: String): Long? =
    this[key].jsonPrimitiveOrNull?.longOrNull

private val JsonElement?.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement?.jsonArrayOrNull: JsonArray?
    get() = this as? JsonArray

private val JsonElement?.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private val IFRAME_BLOCK_REGEX = Regex("""(?is)<iframe\b.*?</iframe>""")
private val BR_TAG_REGEX = Regex("""(?is)<br\s*/?>""")
private val P_START_REGEX = Regex("""(?is)<p\b[^>]*>""")
private val P_END_REGEX = Regex("""(?is)</p\s*>""")
private val HTML_TAG_REGEX = Regex("""(?is)<[^>]+>""")
