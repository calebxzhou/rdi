package calebxzhou.rdi.common.net

import calebxzau.rdi.common.logging.Loggers
import calebxzhou.rdi.common.CommonConfig
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

suspend inline fun httpRequest(crossinline builder: HttpRequestBuilder.() -> Unit): HttpResponse = ktorClient.request(builder)
fun HttpRequestBuilder.json() = contentType(ContentType.Application.Json)
/**
 * Set this before first use of [ktorClient] to override the HTTP cache directory.
 * Defaults to DIR/cache/http.
 */
var httpCacheDir: File = DIR.resolve("cache").resolve("http").apply { mkdirs() }
private const val HTTP_CACHE_SIZE_BYTES = 4 * 1024L * 1024 * 1024 // 4GiB
private const val MAX_DEBUG_TEXT_BODY_BYTES = 64 * 1024L
private val httpLgr by Loggers

val ktorClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                followRedirects(true)
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS)
                proxySelector(DynamicProxySelector())
                cache(Cache(httpCacheDir.apply { mkdirs() }, HTTP_CACHE_SIZE_BYTES))
                configureDebugRequestLogging()
                configureDebugTlsForSelfSigned()
            }
        }
        BrowserUserAgent()
        install(ContentNegotiation) {
            json(serdesJson)

        }
        install(SSE) {
            maxReconnectionAttempts = 4
            reconnectionTime = 5.seconds
            bufferPolicy = SSEBufferPolicy.LastEvents(10)
        }
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
internal fun OkHttpClient.Builder.configureDebugRequestLogging() {
    if (!DEBUG) return
    addInterceptor { chain ->
        val request = chain.request()
        runCatching {
            httpLgr.info { "HTTP ${request.method} ${request.url}" }
            val requestBody = request.body
            val contentType = requestBody?.contentType()
            val contentLength = requestBody?.debugContentLengthOrNull()
            httpLgr.info {
                "Request metadata Content-Type=${contentType ?: "<none>"} " +
                    "Content-Length=${contentLength ?: "<unknown>"}"
            }
            httpLgr.info { "Body ${request.debugBodyForLogging(contentLength)}" }
        }
        chain.proceed(request)
    }
}

internal fun MediaType.isDebugTextType(): Boolean {
    if (type.equals("text", ignoreCase = true)) return true
    if (!type.equals("application", ignoreCase = true)) return false
    return subtype.equals("json", ignoreCase = true) ||
        subtype.endsWith("+json", ignoreCase = true) ||
        subtype.equals("xml", ignoreCase = true) ||
        subtype.endsWith("+xml", ignoreCase = true) ||
        subtype.equals("x-www-form-urlencoded", ignoreCase = true) ||
        subtype.equals("javascript", ignoreCase = true) ||
        subtype.equals("ecmascript", ignoreCase = true)
}

internal fun Request.debugBodyForLogging(): String {
    return debugBodyForLogging(body?.debugContentLengthOrNull())
}

private fun Request.debugBodyForLogging(contentLength: Long?): String {
    val requestBody = body ?: return "<empty>"
    val contentType = requestBody.contentType()
        ?: return "<omitted: unknown>"
    if (contentType.type.equals("multipart", ignoreCase = true)) {
        return "<omitted: multipart>"
    }
    if (!contentType.isDebugTextType()) {
        return "<omitted: binary>"
    }
    if (hasNonIdentityContentEncoding()) {
        return "<omitted: encoded>"
    }
    if (requestBody.isOneShot() || requestBody.isDuplex()) {
        return "<omitted: streaming>"
    }
    if (contentLength == null || contentLength < 0L) {
        return "<omitted: streaming>"
    }
    if (contentLength > MAX_DEBUG_TEXT_BODY_BYTES) {
        return "<omitted: too large>"
    }
    return try {
        Buffer().apply { requestBody.writeTo(this) }
            .readString(contentType.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8)
    } catch (_: Exception) {
        "<omitted: unreadable>"
    }
}

private fun RequestBody.debugContentLengthOrNull(): Long? =
    runCatching { contentLength() }.getOrNull()

private fun Request.hasNonIdentityContentEncoding(): Boolean =
    headers("Content-Encoding")
        .flatMap { it.split(',') }
        .any { !it.trim().equals("identity", ignoreCase = true) }

internal fun OkHttpClient.Builder.configureDebugTlsForSelfSigned() {
    if (!DEBUG) return
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })
    val trustManager = trustAllCerts[0] as X509TrustManager
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }
    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier { _, _ -> true }
}

class DynamicProxySelector(
    private val fallback: ProxySelector? = ProxySelector.getDefault()
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        if (uri.isLoopbackTarget()) return listOf(Proxy.NO_PROXY)

        val cfg = CommonConfig.proxyConfig
        if (!cfg.enabled) return listOf(Proxy.NO_PROXY)
        if (!cfg.systemProxy) {
            if (cfg.host.isBlank() || cfg.port <= 0) return listOf(Proxy.NO_PROXY)
            return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(cfg.host, cfg.port)))
        }
        val selector = ProxySelector.getDefault() ?: fallback ?: return listOf(Proxy.NO_PROXY)
        return selector.select(uri)?.ifEmpty { listOf(Proxy.NO_PROXY) } ?: listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        if (!CommonConfig.proxyConfig.systemProxy) return
        (ProxySelector.getDefault() ?: fallback)?.connectFailed(uri, sa, ioe)
    }
}

private fun URI.isLoopbackTarget(): Boolean {
    val targetHost = host ?: return false
    if (targetHost.isEmpty()) return false
    if (targetHost.equals("localhost", ignoreCase = true)) return true
    if (!targetHost.first().isDigit() && ':' !in targetHost) return false
    return runCatching { InetAddress.getByName(targetHost).isLoopbackAddress }.getOrDefault(false)
}
