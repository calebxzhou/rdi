package calebxzhou.rdi.client.service

import calebxzhou.rdi.client.net.BACKUP_NODE
import calebxzhou.rdi.common.ip2region.CarrierDetectService
import calebxzhou.rdi.common.net.httpRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

private val ipv4Regex =
    Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

internal actual suspend fun tryEnableBackupNodeForPeakHours() {
    //晚高峰非电信备用节点
    if (!isPeakHour()) return
    val carrierName = runCatching {
        detectCarrierNameFromPublicIpv4()
    }.getOrNull() ?: return
    if (!carrierName.contains("电信")) {
        BACKUP_NODE = true
    }
}

private suspend fun detectCarrierNameFromPublicIpv4(): String = withContext(Dispatchers.IO) {
    val ipv4 = httpRequest {
        url("https://ip.3322.net/")
        method = HttpMethod.Get
    }.bodyAsText().trim()
    require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
    CarrierDetectService.detectCarrierName(ipv4)
}

private fun isPeakHour(now: LocalTime = LocalTime.now()): Boolean {
    val start = LocalTime.of(18, 0)
    val end = LocalTime.of(23, 59, 59)
    return !now.isBefore(start) && !now.isAfter(end)
}
