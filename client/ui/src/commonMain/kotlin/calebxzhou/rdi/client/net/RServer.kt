package calebxzhou.rdi.client.net

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.common.DEBUG
import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.RAccount
import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.common.net.*
import calebxzhou.rdi.common.serdesJson
import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

val server
    get() = RServer.now
var loggedAccount: RAccount = RAccount.DEFAULT
val lgr by Loggers
class RServer(
    var ip: String,
    val bgpIp: String,
    val httpPort: Int,
    val httpsPort: Int,
    var gamePort: Int,
) {
    var noHttps = System.getProperty("rdi.noHttps").toBoolean()

    val hqUrl get() = "${if(noHttps) "http" else "https"}://${ip}:${if(noHttps) httpPort else httpsPort}"

    companion object {
        val OFFICIAL_DEBUG = RServer(
            "localhost", "localhost", 65231,65331, 65230
        )
        val OFFICIAL_NNG = RServer(
            "rdi.calebxzhou.cn", "b5rdi.calebxzhou.cn",65231, 65331, 65230
        )
        val now: RServer get() = if (DEBUG) OFFICIAL_DEBUG else OFFICIAL_NNG

    }

    suspend inline fun createRequest(
        path: String,
        method: HttpMethod,
        params: Map<String, Any> = mapOf(),
        crossinline builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse {
        return try {
            httpRequest {
                url("$hqUrl/${path}")
                this.method = method
                if (method != HttpMethod.Get && params.isNotEmpty()) {
                    json()
                    setBody(
                        serdesJson.encodeToString(
                            MapSerializer(String.serializer(), JsonElement.serializer()),
                            params.mapValues {
                                JsonPrimitive(it.value.toString())
                            }
                        ))
                    compress("deflate")
                } else if (method == HttpMethod.Get && params.isNotEmpty()) {
                    params.forEach {
                        parameter(it.key, it.value)
                    }
                }
                accountAuthHeader()
                builder()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lgr.warn(e) { "request failed: $method $path" }
            throw RequestError("无法连接服务器 请检查网络连接")
        }
    }

    suspend inline fun <reified T> makeRequest(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        params: Map<String, Any> = mapOf(),
        crossinline builder: HttpRequestBuilder.() -> Unit = {}
    ): Response<T> {
        val response = createRequest(path, method, params, builder).body<Response<T>>()
        return response
    }


    suspend fun download(
        path: String,
        saveTo: String,
        onProgress: (DownloadProgress) -> Unit
    ) {
        downloadFileTo(
            "${hqUrl}/${path}",
            saveTo,
            headers = mapOf(HttpHeaders.Authorization to "Bearer ${loggedAccount.jwt}"),
            onProgress = onProgress,
        )
    }
}

fun HttpRequestBuilder.accountAuthHeader() {
    header(HttpHeaders.Authorization, "Bearer ${loggedAccount.jwt}")
}

fun CoroutineScope.sse(
    path: String,
    params: Map<String, Any?> = emptyMap(),
    bufferPolicy: SSEBufferPolicy? = null,
    configureRequest: HttpRequestBuilder.() -> Unit = {},
    onError: (Throwable) -> Unit = { throwable ->
        lgr.error { throwable }
    },
    onClosed: suspend () -> Unit = {},
    onEvent: suspend (ServerSentEvent) -> Unit,
) = this.launch {
    val urlString = "${server.hqUrl}/${path.trimStart('/')}"

    if (DEBUG) {
        lgr.info { "[SSE] Connecting to: $urlString" }
    }

    try {
        ktorClient.sse(urlString, {
            accountAuthHeader()
            timeout {
                requestTimeoutMillis = 60000
                socketTimeoutMillis = 60000
            }
            params.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is Iterable<*> -> value.forEach { element -> element?.let { parameter(key, it) } }
                    is Array<*> -> value.forEach { element -> element?.let { parameter(key, it) } }
                    else -> parameter(key, value)
                }
            }
            bufferPolicy?.let { bufferPolicy(it) }
            configureRequest()
        }) {
            if (DEBUG) {
                lgr.info { "[SSE] Connected successfully" }
            }
            try {
                incoming.collect { event ->
                    /*if (DEBUG) {
                        lgr.info { "[SSE] Event: ${event.event}, data: ${event.data?.take(100)}" }
                    }*/
                    when (event.event) {
                        "heartbeat" -> {
                            lgr.info("SSE heartbeat")
                        }
                        "error" -> onError(RequestError(event.data))
                        else -> onEvent(event)
                    }
                }
            } finally {
                if (DEBUG) {
                    lgr.info { "[SSE] Connection closed" }
                }
                onClosed()
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (t: Throwable) {
        lgr.error(t) { "[SSE] Connection failed" }
        onError(t)
        return@launch
    }
}
inline fun CoroutineScope.rdiRequestU(
    path: String,
    method: HttpMethod = HttpMethod.Post,
    params: Map<String, Any> = mapOf(),
    body: String? = null,
    crossinline onDone: () -> Unit = {},
    crossinline onErr: (Throwable) -> Unit,
    crossinline onOk: (Response<Unit>) -> Unit,
) = rdiRequest<Unit>(path, method, params, body, onDone, onErr, onOk)


inline fun <reified T> CoroutineScope.rdiRequest(
    path: String,
    method: HttpMethod = HttpMethod.Get,
    params: Map<String, Any> = mapOf(),
    body: String? = null,
    crossinline onDone: () -> Unit = {},
    crossinline onErr: (Throwable) -> Unit,
    crossinline onOk: (Response<T>) -> Unit,
) = this.launch {
    try {
        val req = withContext(Dispatchers.IO) {
            server.makeRequest<T>(path, method, params) {
                body?.let {
                    json()
                    setBody(it)
                }
            }
        }
        if (req.ok) {
            onOk(req)
        } else {
            throw RequestError(req.msg)
        }
    } catch (cancel: CancellationException) {
        return@launch
    } catch (t: Throwable) {
        onErr(t)
    } finally {
        onDone()
    }
}
