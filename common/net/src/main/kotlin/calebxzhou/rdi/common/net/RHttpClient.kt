package calebxzhou.rdi.common.net

import calebxzhou.rdi.common.CommonConfig
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.DIR
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.*
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
private const val HTTP_CACHE_SIZE_BYTES = 4*1024L * 1024 * 1024 // 1GB

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
                configureDebugTlsForSelfSigned()
            }
        }
        BrowserUserAgent()
        install(ContentNegotiation) {
            json(serdesJson)

        }
        install(HttpCache) {
            publicStorage(FileStorage(httpCacheDir))
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
