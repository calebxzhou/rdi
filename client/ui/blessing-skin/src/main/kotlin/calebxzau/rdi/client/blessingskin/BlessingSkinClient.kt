package calebxzau.rdi.client.blessingskin

import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class BlessingSkinClient (
    private val baseUrl: String,
    private val httpClient: HttpClient= ktorClient,
    private val json: Json= serdesJson,
    private val pageDelayMillis: Long= DEFAULT_PAGE_DELAY_MILLIS
) {


    init {
        require(baseUrl.isNotBlank()) { "Blessing Skin base URL不能为空" }
    }

    suspend fun search(
        request: BlessingTextureSearch,
        page: Int
    ): Result<BlessingTexturePage> = runCatching {
        require(page >= 1) { "Blessing Skin页码必须从1开始" }

        val firstProviderPage = (page - 1) * PROVIDER_PAGES_PER_REQUEST + 1
        val providerPages = buildList {
            for (offset in 0 until PROVIDER_PAGES_PER_REQUEST) {
                if (offset > 0 && pageDelayMillis > 0) {
                    delay(pageDelayMillis)
                }
                val providerPage = fetchPage(request, firstProviderPage + offset)
                add(providerPage)
                if (
                    (providerPage.lastPage != null && providerPage.currentPage >= providerPage.lastPage) ||
                    (providerPage.lastPage == null && providerPage.data.size < PROVIDER_PAGE_SIZE)
                ) {
                    break
                }
            }
        }

        BlessingTexturePage(
            items = providerPages.flatMap { it.data.map(::toSummary) },
            nextPage = (page + 1).takeIf {
                providerPages.any { providerPage ->
                    (providerPage.lastPage != null && providerPage.currentPage < providerPage.lastPage) ||
                        (providerPage.lastPage == null && providerPage.data.size >= PROVIDER_PAGE_SIZE)
                }
            }
        )
    }

    suspend fun resolve(textureId: Int): Result<ResolvedBlessingTexture> = runCatching {
        require(textureId > 0) { "Blessing Skin材质ID必须大于0" }
        val texture = decodeTexture(fetch("/texture/$textureId"))
        val type = parseType(texture.type)
        ResolvedBlessingTexture(
            id = texture.tid,
            name = texture.name,
            type = type,
            uploaderId = texture.uploader,
            isPublic = texture.public,
            likes = texture.likes,
            hash = texture.hash,
            textureUrl = textureUrl(texture.hash),
            previewUrl = previewUrl(texture.tid)
        )
    }

    fun previewUrl(textureId: Int, height: Int = DEFAULT_PREVIEW_HEIGHT): String {
        require(textureId > 0) { "Blessing Skin材质ID必须大于0" }
        require(height > 0) { "Blessing Skin预览高度必须大于0" }
        return buildUrl(
            path = "/preview/$textureId",
            query = mapOf("height" to height.toString(), "png" to "")
        )
    }

    private suspend fun fetchPage(
        request: BlessingTextureSearch,
        page: Int
    ): TexturePageDto {
        val body = fetch(
            path = "/skinlib/list",
            query = mapOf(
                "filter" to request.filter.wireValue,
                "sort" to request.sort.wireValue,
                "page" to page.toString(),
                "keyword" to request.keyword
            )
        )
        return try {
            json.decodeFromString<TexturePageDto>(body)
        } catch (cause: Exception) {
            throw BlessingSkinException.InvalidResponse("无法解析Blessing Skin列表响应", cause)
        }
    }

    private suspend fun fetch(
        path: String,
        query: Map<String, String> = emptyMap()
    ): String {
        val response = httpClient.get(buildUrl(path, query))
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw BlessingSkinException.HttpFailure(response.status.value, body)
        }
        return body
    }

    private fun decodeTexture(body: String): TextureDto = try {
        json.decodeFromString<TextureDto>(body)
    } catch (cause: Exception) {
        throw BlessingSkinException.InvalidResponse("无法解析Blessing Skin材质响应", cause)
    }

    private fun toSummary(dto: TextureSummaryDto): BlessingTextureSummary {
        val type = parseType(dto.type)
        return BlessingTextureSummary(
            id = dto.tid,
            name = dto.name,
            type = type,
            uploaderId = dto.uploader,
            isPublic = dto.public,
            likes = dto.likes,
            previewUrl = previewUrl(dto.tid)
        )
    }

    private fun parseType(value: String): BlessingTextureType =
        BlessingTextureType.fromWireValue(value)
            ?: throw BlessingSkinException.InvalidResponse("未知的Blessing Skin材质类型: $value")

    private fun textureUrl(hash: String): String = buildUrl("/textures/$hash")

    private fun buildUrl(
        path: String,
        query: Map<String, String> = emptyMap()
    ): String = URLBuilder(baseUrl).apply {
        encodedPath = encodedPath.trimEnd('/') + path
        query.forEach { (name, value) -> parameters.append(name, value) }
    }.buildString()

    @Serializable
    private data class TexturePageDto(
        @SerialName("current_page") val currentPage: Int,
        @SerialName("last_page") val lastPage: Int? = null,
        val data: List<TextureSummaryDto>
    )

    @Serializable
    private data class TextureSummaryDto(
        val tid: Int,
        val name: String,
        val type: String,
        val uploader: Int,
        val public: Boolean,
        val likes: Int
    )

    @Serializable
    private data class TextureDto(
        val tid: Int,
        val name: String,
        val type: String,
        val hash: String,
        val uploader: Int,
        val public: Boolean,
        val likes: Int
    )

    private companion object {
        const val PROVIDER_PAGE_SIZE = 20
        const val PROVIDER_PAGES_PER_REQUEST = 2
        const val DEFAULT_PAGE_DELAY_MILLIS = 300L
        const val DEFAULT_PREVIEW_HEIGHT = 150
    }
}
