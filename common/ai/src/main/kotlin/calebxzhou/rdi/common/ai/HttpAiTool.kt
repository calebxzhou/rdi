package calebxzhou.rdi.common.ai

import calebxzhou.rdi.common.net.DynamicProxySelector
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

object HttpAiTool : AiTool {
    override val name = "http_request"
    private const val MAX_TOOL_RESPONSE_BYTES = 64 * 1024 * 1024
    private const val MAX_TOOL_CONTENT_CHARS = 32 * 1024
    private val allowedToolHosts = setOf("minecraft.wiki", "mcmod.cn", "bilibili.com")

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                config {
                    followRedirects(false)
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
        description = "Send a GET or POST HTTP request to whitelisted Minecraft-related websites or localhost. DEBUG=${context.debug}. ${if (context.debug) "Full localhost/R-MCP URLs may be shown in visible thinking or response text." else "Never expose full localhost/R-MCP URLs in visible thinking or response text; refer to them as R-MCP."}",
        parameters = AiToolParameters(
            properties = mapOf(
                "method" to AiToolProperty(type = "string", enum = listOf("GET", "POST")),
                "url" to AiToolProperty(type = "string"),
                "body" to AiToolProperty(type = "string"),
                "contentType" to AiToolProperty(type = "string")
            ),
            required = listOf("method", "url")
        )
    )

    override suspend fun execute(argumentsJson: String, context: AiToolContext): AiToolExecution {
        val args = decodeArgs<HttpRequestToolArgs>(argumentsJson) ?: return argError(name)
        val uri = runCatching { validateToolUrl(args.url) }.getOrElse {
            val message = it.message ?: "URL不在允许访问范围内"
            return AiToolExecution(result = AiToolResult(tool = name, target = sanitizeToolTarget(args.url, context.debug), error = message))
        }
        val visibleTarget = sanitizeToolTarget(uri, context.debug)
        val method = runCatching { parseToolMethod(args.method) }.getOrElse {
            val message = it.message ?: "只允许GET和POST请求"
            return AiToolExecution(result = AiToolResult(tool = name, target = visibleTarget, error = message))
        }
        val access = AiToolAccess(visibleTarget)
        val result = runCatching {
            val response = client.request {
                url(uri.toString())
                this.method = method
                header(HttpHeaders.Accept, "text/html,application/json,text/plain,*/*")
                if (method == HttpMethod.Post) {
                    contentType(args.contentType.toContentType())
                    setBody(args.body.orEmpty())
                }
            }
            val body = readLimitedResponse(response.body())
            val text = String(body, Charsets.UTF_8)
            AiToolResult(
                tool = name,
                target = visibleTarget,
                status = response.status.value,
                contentType = response.headers[HttpHeaders.ContentType],
                output = text.take(MAX_TOOL_CONTENT_CHARS),
                outputBytes = body.size,
                truncated = text.length > MAX_TOOL_CONTENT_CHARS
            )
        }.getOrElse {
            AiToolResult(tool = name, target = visibleTarget, error = it.message ?: "HTTP工具请求失败")
        }
        return AiToolExecution(access, result, rmcpDetail(uri, method, args.body.orEmpty(), result))
    }

    private suspend fun readLimitedResponse(channel: ByteReadChannel): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!channel.isClosedForRead) {
            val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
            if (bytesRead == -1) break
            if (bytesRead == 0) continue
            if (output.size() + bytesRead > MAX_TOOL_RESPONSE_BYTES) {
                throw IllegalStateException("HTTP工具响应超过64MB")
            }
            output.write(buffer, 0, bytesRead)
        }
        return output.toByteArray()
    }

    private fun validateToolUrl(rawUrl: String): URI {
        val uri = URI(rawUrl.trim())
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "AI工具只允许访问http/https URL" }
        val host = uri.host?.trimEnd('.')?.lowercase()
        require(!host.isNullOrBlank()) { "AI工具URL缺少host" }
        require(isAllowedToolHost(host)) { "AI工具不允许访问该URL" }
        return uri
    }

    private fun isAllowedToolHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        return allowedToolHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    private fun rmcpDetail(uri: URI, method: HttpMethod, body: String, result: AiToolResult): AiToolDetail? {
        if (!uri.isLocalhost()) return null
        return AiToolDetail(
            method = if (method == HttpMethod.Get) "GET" else "POST",
            path = uri.pathAndQuery(),
            payload = if (method == HttpMethod.Post) body else "",
            response = result.output ?: result.error.orEmpty(),
            status = result.status
        )
    }

    private fun URI.isLocalhost(): Boolean {
        val host = host?.trimEnd('.')?.lowercase()
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    private fun URI.pathAndQuery(): String {
        val path = rawPath?.takeIf(String::isNotBlank) ?: "/"
        return rawQuery?.takeIf(String::isNotBlank)?.let { "$path?$it" } ?: path
    }

    private fun sanitizeToolTarget(rawTarget: String, debug: Boolean): String {
        val uri = runCatching { URI(rawTarget.trim()) }.getOrNull() ?: return rawTarget
        return sanitizeToolTarget(uri, debug)
    }

    private fun sanitizeToolTarget(uri: URI, debug: Boolean): String {
        val host = uri.host?.trimEnd('.')?.lowercase()
        return if (!debug && (host == "localhost" || host == "127.0.0.1" || host == "::1")) "R-MCP" else uri.toString()
    }

    private fun parseToolMethod(method: String): HttpMethod =
        when (method.trim().uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            else -> throw IllegalArgumentException("AI工具只允许GET和POST请求")
        }

    private fun String?.toContentType(): ContentType {
        val value = this?.trim().takeUnless { it.isNullOrBlank() } ?: return ContentType.Application.Json
        return runCatching { ContentType.parse(value) }.getOrDefault(ContentType.Application.Json)
    }
}

@Serializable
private data class HttpRequestToolArgs(
    val method: String,
    val url: String,
    val body: String? = null,
    val contentType: String? = null
)
