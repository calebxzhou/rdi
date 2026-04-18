package calebxzhou.rdi.master.service

import org.lionsoul.ip2region.service.Config
import org.lionsoul.ip2region.service.Ip2Region

object CarrierDetectService {
    private const val XDB_RESOURCE_PATH = "ip2region_v4.xdb"
    private val ipv4Regex =
        Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

    private val ip2Region: Ip2Region by lazy {
        val v4Config = Config.custom()
            .setCachePolicy(Config.FullCache)
            .setXdbInputStream(
                CarrierDetectService::class.java.classLoader.getResourceAsStream(XDB_RESOURCE_PATH)
                    ?: error("找不到资源 $XDB_RESOURCE_PATH")
            )
            .setSearchers(4)
            .asV4()
        Ip2Region.create(v4Config, null)
    }

    fun detectResult(ipv4: String): Ip2RegionResult {
        require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
        val region = ip2Region.search(ipv4).trim()
        return Ip2RegionResult.parse(region)
    }

    fun detectCarrierName(ipv4: String) = detectResult(ipv4).carrierName
}

data class Ip2RegionResult(
    val raw: String,
    val country: String,
    val province: String,
    val city: String,
    val isp: String,
    val countryCode: String,
) {
    val carrierName
        get() = isp.ifBlank { "未知" }

    val isCT
        get() = carrierName == "电信"

    val isChina
        get() = countryCode == "CN"

    companion object {
        private const val EMPTY = "0"

        fun parse(raw: String): Ip2RegionResult {
            val parts = raw.trim().split('|')
            return Ip2RegionResult(
                raw = raw.trim(),
                country = parts.getOrNull(0).normalizePart(),
                province = parts.getOrNull(1).normalizePart(),
                city = parts.getOrNull(2).normalizePart(),
                //中国电信->电信
                isp = parts.getOrNull(3).normalizePart().removePrefix("中国"),
                countryCode = parts.getOrNull(4).normalizePart(),
            )
        }

        private fun String?.normalizePart() =
            this?.trim()
                ?.takeUnless { it.isEmpty() || it == EMPTY }
                .orEmpty()
    }
}
