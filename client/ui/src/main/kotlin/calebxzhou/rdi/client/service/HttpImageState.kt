package calebxzhou.rdi.client.service

import androidx.compose.ui.graphics.ImageBitmap
import calebxzau.rdi.client.lgr
import calebxzau.rdi.client.ui.decodeImageBitmap
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class HttpImageState(
    val bitmap: ImageBitmap?,
    val error: String?,
    val isLoading: Boolean
) {
    companion object {
        private const val SUCCESS_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val FAILURE_CACHE_TTL_MS = 30 * 1000L
        private val memoryCache = ConcurrentHashMap<String, CachedHttpImageState>()
        private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<HttpImageState>>()
        private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun loading(): HttpImageState = HttpImageState(null, null, true)
        fun peek(url: String): HttpImageState? = peekCached(url.trim())

        suspend fun fetch(url: String): HttpImageState {
            val normalizedUrl = url.trim()
            if (normalizedUrl.isBlank()) {
                return failed()
            }
            peekCached(normalizedUrl)?.let { return it }
            val waiting = CompletableDeferred<HttpImageState>()
            val deferred = inFlightRequests.putIfAbsent(normalizedUrl, waiting)?.also {
                waiting.cancel()
            } ?: waiting.also {
                startFetch(normalizedUrl, it)
            }
            return deferred.await()
        }

        private fun startFetch(url: String, waiting: CompletableDeferred<HttpImageState>) {
            fetchScope.launch {
                try {
                    val result = loadImageState(url)
                    putCache(url, result)
                    waiting.complete(result)
                } catch (err: CancellationException) {
                    clearInFlight(url)
                    waiting.cancel(err)
                    throw err
                } catch (error: Throwable) {
                    lgr.warn(error) { "网络图片加载失败: $url" }
                    val result = failed()
                    putCache(url, result)
                    waiting.complete(result)
                }
            }
        }

        private suspend fun loadImageState(url: String): HttpImageState {
            val response = httpRequest { url(url) }
            if (!response.status.isSuccess()) {
                return failed()
            }
            val bytes = response.bodyAsBytes()
            val bitmap = decodeImageBitmap(bytes).getOrThrow()
            return HttpImageState(bitmap, null, false)
        }

        private fun peekCached(url: String): HttpImageState? {
            if (url.isBlank()) return null
            val cached = memoryCache[url] ?: return null
            if (!cached.isValid()) {
                memoryCache.remove(url, cached)
                return null
            }
            return cached.state
        }

        private fun putCache(url: String, state: HttpImageState) {
            memoryCache[url] = CachedHttpImageState(
                state = state,
                expiresAtMs = System.currentTimeMillis() + cacheTtlMs(state)
            )
            inFlightRequests.remove(url)
        }

        private fun clearInFlight(url: String) {
            inFlightRequests.remove(url)
        }

        private fun cacheTtlMs(state: HttpImageState): Long = if (state.bitmap != null) {
            SUCCESS_CACHE_TTL_MS
        } else {
            FAILURE_CACHE_TTL_MS
        }

        private fun failed(): HttpImageState = HttpImageState(null, "图片加载失败", false)
    }
}

private data class CachedHttpImageState(
    val state: HttpImageState,
    val expiresAtMs: Long
) {
    fun isValid(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < expiresAtMs
}
