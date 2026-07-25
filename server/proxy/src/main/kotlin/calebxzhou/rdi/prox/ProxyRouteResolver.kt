package calebxzhou.rdi.prox

import calebxzhou.rdi.common.exception.RequestError
import calebxzhou.rdi.common.model.ProxyHostRoute
import calebxzhou.rdi.common.model.Response
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.withTimeout

class ProxyRouteResolver(
    private val request: suspend (Int) -> Response<ProxyHostRoute> = { port ->
        ktorClient.get("$MASTER_URL/host/route?port=$port").body()
    },
    private val timeoutMillis: Long = 3_000
) {
    suspend fun resolve(port: Int): Result<ProxyHostRoute> = runCatching {
        withTimeout(timeoutMillis) { request(port) }.run {
            data ?: throw RequestError("无法获取房间路由：$msg")
        }
    }
}
