package calebxzhou.rdi.prox

import calebxzhou.rdi.common.serdesJson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.SSEBufferPolicy
import io.ktor.serialization.kotlinx.json.json
import io.netty.util.AttributeKey
import okhttp3.Cache
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds


val MASTER_URL = System.getProperty("rdi.master")?:"http://127.0.0.1:65231"
val lgr = KotlinLogging.logger {  }

val ktorClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        engine {
            config {
                followRedirects(true)
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(serdesJson)
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

/**
 * calebxzhou @ 8/31/2025 5:20 PM
 */

fun main() {
    lgr.info { "proxy minecraft frame relay enabled" }
    val proxy = TcpReverseProxy()
    proxy.start("0.0.0.0", Const.SERVER_PORT, Const.BACKEND_HOST, 25565)
}
