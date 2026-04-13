package calebxzhou.rdi.common.ip2region

import org.lionsoul.ip2region.service.Config
import org.lionsoul.ip2region.service.Ip2Region

/**
 * 独立ip2region模块(module)入口。
 */
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

    fun detectCarrierName(ipv4: String): String {
        require(ipv4Regex.matches(ipv4)) { "无效IPv4: $ipv4" }
        //中国|广东省|深圳市|电信|CN
        val region = ip2Region.search(ipv4).trim()
        if (region.isBlank()) return "未知"
        return region
            .split('|')
            .getOrNull(3)
            ?.trim()
            ?: "未知"
    }
}
