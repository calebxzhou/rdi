package calebxzhou.rdi.common.ip2region

import org.lionsoul.ip2region.service.Config
import org.lionsoul.ip2region.service.Ip2Region

actual object CarrierDetectService {
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

    actual fun detectResult(ipv4: String): Ip2RegionResult {
        require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
        val region = ip2Region.search(ipv4).trim()
        return Ip2RegionResult.parse(region)
    }

    actual fun detectCarrierName(ipv4: String) = detectResult(ipv4).carrierName
}
