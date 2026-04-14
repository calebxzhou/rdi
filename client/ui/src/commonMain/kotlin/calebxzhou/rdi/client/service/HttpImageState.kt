package calebxzhou.rdi.client.service

import androidx.compose.ui.graphics.ImageBitmap
import calebxzhou.rdi.client.ui.decodeImageBitmap
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

data class HttpImageState(
    val bitmap: ImageBitmap?,
    val error: String?,
    val isLoading: Boolean
) {
    companion object {
        fun loading(): HttpImageState = HttpImageState(null, null, true)
        suspend fun fetch(url: String): HttpImageState {
            return runCatching {
                val response = httpRequest { url(url) }
                if (!response.status.isSuccess()) {
                    return HttpImageState(null, "图片加载失败", false)
                }
                val bytes = response.bodyAsBytes()
                val bitmap = decodeImageBitmap(bytes)
                HttpImageState(bitmap, null, false)
            }.getOrElse {
                HttpImageState(null, "图片加载失败", false)
            }
        }
    }
}