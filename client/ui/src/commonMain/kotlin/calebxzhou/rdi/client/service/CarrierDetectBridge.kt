package calebxzhou.rdi.client.service

import calebxzhou.mykotutils.log.Loggers
import calebxzhou.rdi.CONF
import calebxzhou.rdi.client.AppConfig
import calebxzhou.rdi.client.net.BACKUP_NODE
import calebxzhou.rdi.client.net.RServer
import calebxzhou.rdi.client.net.SERVER_NODES
import calebxzhou.rdi.common.ip2region.CarrierDetectService
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.net.httpRequest
import calebxzhou.rdi.lgr
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val ipv4Regex =
    Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")


internal suspend fun refreshNodeSettings() {
    val regionResult = runCatching {
        detectRegionFromPublicIpv4()
    }.getOrElse {
        lgr.warn(it) { "无法读取ip信息" }
        return
    }
    lgr.info { regionResult }
    val matchedCarrier = SERVER_NODES.values
        .firstOrNull { node -> node.applyPred(regionResult) }
        ?.id
    matchedCarrier?.takeIf { it != CONF.carrier }?.let { carrier ->
        CONF = CONF.copy(carrier = carrier)
        AppConfig.save(CONF)
        lgr.info { "根据ip2region自动选择运营商节点${SERVER_NODES[carrier]?.name}($carrier)" }
    }
    if (isPeakHour() && !regionResult.isCT) {
        BACKUP_NODE = true
        lgr.info { "启用晚高峰备用节点1" }
    }

}

private suspend fun detectRegionFromPublicIpv4() = withContext(Dispatchers.IO) {
    val ipv4 = httpRequest {
        url("https://ip.3322.net/")
        method = HttpMethod.Get
    }.bodyAsText().trim()
    require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
    CarrierDetectService.detectResult(ipv4)
}

private fun isPeakHour(): Boolean {
    val hour = kotlin.time.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .hour
    return hour in 18..23
}
