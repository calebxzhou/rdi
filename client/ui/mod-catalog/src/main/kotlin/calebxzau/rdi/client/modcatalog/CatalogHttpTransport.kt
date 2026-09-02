package calebxzau.rdi.client.modcatalog

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal data class CatalogHttpResponse(
    val method: HttpMethod,
    val url: String,
    val requestBody: String?,
    val status: HttpStatusCode,
    val body: String,
    val contentType: ContentType?,
    val retryAfterMillis: Long?,
    val onInvalid: (Throwable?) -> CatalogException.InvalidResponse,
    val decodeFallback: (suspend () -> CatalogHttpResponse)? = null
) {
    fun invalid(cause: Throwable? = null): CatalogException.InvalidResponse = onInvalid(
        IllegalStateException(
            """method=${method.value}
url=$url
requestBody=${requestBody.orEmpty()}
status=${status.value}
contentType=$contentType
responseBody=$body""",
            cause
        )
    )
}

internal class CatalogHttpTransport(
    private val httpClient: HttpClient,
    private val platform: ModPlatform,
    private val officialBaseUrl: String,
    private val mirrorBaseUrl: String,
    private val defaultHeaders: Map<String, String>,
    private val preferMirror: () -> Boolean,
    private val json: Json,
    private val onWarning: (Throwable) -> Unit
) {
    suspend fun get(
        path: String,
        parameters: Map<String, String> = emptyMap(),
        allowedStatuses: Set<HttpStatusCode> = emptySet()
    ): CatalogHttpResponse = execute(path, HttpMethod.Get, parameters, null, allowedStatuses)

    suspend fun post(
        path: String,
        body: String,
        allowedStatuses: Set<HttpStatusCode> = emptySet()
    ): CatalogHttpResponse = execute(path, HttpMethod.Post, emptyMap(), body, allowedStatuses)

    private suspend fun execute(
        path: String,
        method: HttpMethod,
        parameters: Map<String, String>,
        body: String?,
        allowedStatuses: Set<HttpStatusCode>
    ): CatalogHttpResponse {
        if (preferMirror()) {
            try {
                val mirrorResponse = request(mirrorBaseUrl, path, method, parameters, body)
                val invalidCause = mirrorResponse.invalidCause()
                if (invalidCause == null) {
                    return mirrorResponse.copy(
                        decodeFallback = {
                            executeOfficial(path, method, parameters, body, allowedStatuses)
                        }
                    )
                }
                // invalid() records the mirror response; its exception is discarded so the official request can run.
                mirrorResponse.invalid(invalidCause)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: CatalogException.InvalidResponse) {
                // The invalid mirror response was already recorded. Continue with the official endpoint.
            } catch (cause: Exception) {
                onWarning(cause)
            }
        }
        return executeOfficial(path, method, parameters, body, allowedStatuses)
    }

    private suspend fun executeOfficial(
        path: String,
        method: HttpMethod,
        parameters: Map<String, String>,
        body: String?,
        allowedStatuses: Set<HttpStatusCode>
    ): CatalogHttpResponse {
        var lastFailure: Throwable? = null
        repeat(2) { attempt ->
            try {
                val official = request(officialBaseUrl, path, method, parameters, body)
                if (official.status.value in TRANSIENT_STATUSES && attempt == 0) {
                    lastFailure = official.invalid(official.invalidCause())
                    delay(RETRY_DELAY_MILLIS)
                    return@repeat
                }
                return classify(official, allowedStatuses).also(::validateOfficialResponse)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                if (!cause.isTransientTransportFailure() || attempt > 0) {
                    throw cause.toCatalogTransportFailure()
                }
                lastFailure = cause
                delay(RETRY_DELAY_MILLIS)
            }
        }
        throw (lastFailure ?: IllegalStateException("Request failed")).toCatalogTransportFailure()
    }

    private suspend fun request(
        baseUrl: String,
        path: String,
        method: HttpMethod,
        parameters: Map<String, String>,
        body: String?
    ): CatalogHttpResponse {
        val response = httpClient.request("${baseUrl.trimEnd('/')}/${path.trimStart('/')}") {
            this.method = method
            defaultHeaders.forEach { (name, value) -> header(name, value) }
            accept(ContentType.Application.Json)
            parameters.forEach { (name, value) -> parameter(name, value) }
            body?.let {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody(it)
            }
        }
        return CatalogHttpResponse(
            method = method,
            url = response.request.url.toString(),
            requestBody = body,
            status = response.status,
            body = response.bodyAsText(),
            contentType = response.contentType(),
            retryAfterMillis = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.times(1_000),
            onInvalid = { cause ->
                CatalogException.InvalidResponse(platform, cause).also(onWarning)
            }
        )
    }

    private fun CatalogHttpResponse.invalidCause(): Throwable? {
        if (status.value !in 200..299) return IllegalStateException("HTTP ${status.value}")
        if (contentType?.match(ContentType.Application.Json) != true) {
            return IllegalStateException("Unexpected Content-Type: $contentType")
        }
        try {
            json.parseToJsonElement(body)
        } catch (cause: SerializationException) {
            return cause
        } catch (cause: IllegalArgumentException) {
            return cause
        }
        return null
    }

    private fun classify(
        response: CatalogHttpResponse,
        allowedStatuses: Set<HttpStatusCode>
    ): CatalogHttpResponse {
        if (response.status.value in 200..299 || response.status in allowedStatuses) return response
        throw when (response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> CatalogException.Unauthorized(platform)
            HttpStatusCode.TooManyRequests -> CatalogException.RateLimited(platform, response.retryAfterMillis)
            else -> response.invalid(IllegalStateException("HTTP ${response.status.value}"))
        }
    }

    private fun validateOfficialResponse(response: CatalogHttpResponse) {
        if (response.status.value !in 200..299) return
        response.invalidCause()?.let { throw response.invalid(it) }
    }

    private fun Throwable.isTransientTransportFailure(): Boolean =
        this is IOException || this is HttpRequestTimeoutException

    private fun Throwable.toCatalogTransportFailure(): Throwable = when (this) {
        is CatalogException -> this
        else -> CatalogException.Network(platform, this)
    }

    companion object {
        private val TRANSIENT_STATUSES = setOf(502, 503, 504)
        private const val RETRY_DELAY_MILLIS = 200L
    }
}

internal suspend inline fun <reified T> CatalogHttpResponse.decode(
    platform: ModPlatform,
    json: Json
): T {
    var response = this
    while (true) {
        try {
            return json.decodeFromString(response.body)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            val failure = response.invalid(cause)
            response = response.decodeFallback?.invoke() ?: throw failure
        }
    }
}
