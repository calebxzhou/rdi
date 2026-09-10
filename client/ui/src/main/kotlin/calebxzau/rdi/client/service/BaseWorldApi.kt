package calebxzau.rdi.client.service

import calebxzhou.rdi.client.net.loggedAccount
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.client.net.rdiResponse
import calebxzau.rdi.common.model.BaseWorld
import calebxzau.rdi.common.model.BaseWorldUploadSessionCreateDto
import calebxzau.rdi.common.model.BaseWorldUploadSessionVo
import calebxzhou.rdi.common.net.ktorClient
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.timeout
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import java.util.UUID
import java.io.IOException

data class BaseWorldUploadRequest(
    val directory: java.io.File?,
    val name: String,
    val levelType: String,
    val generatorSettings: String?,
)

interface BaseWorldApi {
    suspend fun list(): List<BaseWorld>
    suspend fun listReady(): List<BaseWorld> =
        error("ready-only地图模板列表暂不可用")
    suspend fun create(dto: BaseWorld.CreateDto): BaseWorld
    suspend fun createUpload(worldId: UUID, dto: BaseWorldUploadSessionCreateDto): BaseWorldUploadSessionVo
    suspend fun uploadStatus(worldId: UUID, uploadId: UUID): BaseWorldUploadSessionVo
    suspend fun uploadPart(worldId: UUID, uploadId: UUID, index: Int, bytes: ByteArray, sha1: String)
    suspend fun completeUpload(worldId: UUID, uploadId: UUID): BaseWorldUploadSessionVo
    suspend fun cancelUpload(worldId: UUID, uploadId: UUID)
    suspend fun rename(worldId: UUID, dto: BaseWorld.NameUpdateDto): BaseWorld =
        error("地图模板改名暂不可用")
    suspend fun delete(worldId: UUID)
}

/**
 * Creates an API client with a snapshot of the selected server and account.
 * Upload tasks can therefore continue against their original account even if
 * the active account or route changes while the task is running.
 */
fun currentBaseWorldApi(httpClient: HttpClient = ktorClient): BaseWorldApi {
    val token = loggedAccount.jwt?.trim()?.takeIf(String::isNotEmpty)
        ?: error("必须登录后才能上传地图模板")
    return HttpBaseWorldApi(httpClient, server.hqUrl.trimEnd('/'), token)
}

private class HttpBaseWorldApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val token: String,
) : BaseWorldApi {
    override suspend fun list(): List<BaseWorld> =
        request(HttpMethod.Get, "/baseworld?myOnly=true")

    override suspend fun listReady(): List<BaseWorld> =
        request(HttpMethod.Get, "/baseworld?myOnly=false")

    override suspend fun create(dto: BaseWorld.CreateDto): BaseWorld =
        request<BaseWorld>(HttpMethod.Post, "/baseworld", serdesJson.encodeToString(dto))

    override suspend fun createUpload(
        worldId: UUID,
        dto: BaseWorldUploadSessionCreateDto,
    ): BaseWorldUploadSessionVo = request(
        HttpMethod.Post,
        "/baseworld/$worldId/upload",
        serdesJson.encodeToString(dto),
    )

    override suspend fun uploadStatus(worldId: UUID, uploadId: UUID): BaseWorldUploadSessionVo =
        request<BaseWorldUploadSessionVo>(HttpMethod.Get, "/baseworld/$worldId/upload/$uploadId")

    override suspend fun uploadPart(
        worldId: UUID,
        uploadId: UUID,
        index: Int,
        bytes: ByteArray,
        sha1: String,
    ) {
        request<Unit>(HttpMethod.Put, "/baseworld/$worldId/upload/$uploadId/parts/$index") {
            header("X-Part-SHA1", sha1)
            contentType(ContentType.Application.OctetStream)
            setBody(bytes)
            timeout {
                requestTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
                socketTimeoutMillis = UPLOAD_TIMEOUT_MILLIS
            }
        }
    }

    override suspend fun completeUpload(worldId: UUID, uploadId: UUID): BaseWorldUploadSessionVo =
        request(HttpMethod.Post, "/baseworld/$worldId/upload/$uploadId/complete")

    override suspend fun cancelUpload(worldId: UUID, uploadId: UUID) {
        request<Unit>(HttpMethod.Delete, "/baseworld/$worldId/upload/$uploadId")
    }

    override suspend fun rename(worldId: UUID, dto: BaseWorld.NameUpdateDto): BaseWorld =
        request(HttpMethod.Put, "/baseworld/$worldId", serdesJson.encodeToString(dto))

    override suspend fun delete(worldId: UUID) {
        request<Unit>(HttpMethod.Delete, "/baseworld/$worldId")
    }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        body: String? = null,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = httpClient.request("$baseUrl$path") {
            this.method = method
            header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            configure()
        }
        if (response.status.value >= 500) {
            val status = response.status.value
            response.bodyAsText()
            throw IOException("服务器暂时无法处理地图模板上传:HTTP$status")
        }
        if (T::class == Unit::class) {
            val envelope = response.rdiResponse<JsonElement>()
            if (!envelope.ok) {
                throw calebxzhou.rdi.common.exception.RequestError(
                    envelope.msg,
                    errorCode = envelope.errorCode,
                )
            }
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }
        val envelope = response.rdiResponse<T>()
        if (!envelope.ok) {
            throw calebxzhou.rdi.common.exception.RequestError(
                envelope.msg,
                errorCode = envelope.errorCode,
            )
        }
        return envelope.data
            ?: if (T::class == Unit::class) Unit as T
            else error("服务器响应缺少地图模板数据")
    }
}

private const val UPLOAD_TIMEOUT_MILLIS = 10 * 60 * 1000L
