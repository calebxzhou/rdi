package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.server
import calebxzhou.rdi.common.model.ServerEntry
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.lgr
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ipv4Regex =
    Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")


internal suspend fun refreshNodeSettings() {
    val ipv4 = runCatching { detectPublicIpv4() }.getOrElse {
        lgr.warn(it) { "无法读取ip信息" }
        return
    }
    val entry = runCatching {
        server.makeRequest<ServerEntry>("server-entry", params = mapOf("myIp" to ipv4)).data
    }.getOrElse {
        lgr.warn(it) { "获取游戏节点失败，继续使用本地回退节点 " }
        return
    } ?: return
    RServer.updateServerEntry(entry)
    val routeState = RServer.routeState.value
    lgr.info { "服务端为当前IP选择节点${routeState.nodeName ?: entry.nodeName} -> ${RServer.currentGameAddr}，backup=${routeState.useBackupNode}" }
}

private suspend fun detectPublicIpv4() = withContext(Dispatchers.IO) {
    val ipv4 = httpRequest {
        url("https://ip.3322.net/")
        method = HttpMethod.Get
    }.bodyAsText().trim()
    require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
    ipv4
}
