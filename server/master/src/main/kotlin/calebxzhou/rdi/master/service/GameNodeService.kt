package calebxzhou.rdi.master.service

import calebxzhou.rdi.common.model.ServerEntry
import calebxzhou.rdi.master.CONF
import calebxzhou.rdi.master.GameNodeRuleConfig
import calebxzhou.rdi.master.exception.ParamError
import calebxzhou.rdi.master.net.param
import calebxzhou.rdi.master.net.response
import io.ktor.server.routing.*

private val ipv4Regex =
    Regex("""^(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$""")

fun Route.gameNodeRoutes() {
    get("/server-entry") {
        response(data = GameNodeService.resolveServerEntry(call.param("myIp")))
    }
}

object GameNodeService {
    fun resolveServerEntry(ipv4: String): ServerEntry {
        requireIpv4(ipv4)
        val region = CarrierDetectService.detectResult(ipv4)
        val node = selectNode(region)
        val backupApi = CONF.server.bgpUrl.trim()
        val useBackupNode = backupApi.isNotBlank() && !region.isCT
        return ServerEntry(
            api = backupApi.takeIf { useBackupNode },
            useBackupNode = useBackupNode,
            nodeName = node.name,
            gameAddr = node.gameAddr
        )
    }

    private fun requireIpv4(ipv4: String) {
        if (!ipv4Regex.matches(ipv4)) {
            throw ParamError("myIp格式错误")
        }
    }

    private fun selectNode(region: Ip2RegionResult): GameNodeRuleConfig {
        val nodes = CONF.gameNode.nodes
        return nodes.firstOrNull { it.matches(region) }
            ?: nodes.firstOrNull()
            ?: throw ParamError("未配置游戏节点")
    }

    private fun GameNodeRuleConfig.matches(region: Ip2RegionResult): Boolean {
        // `matchOutsideChina`是兜底的国际节点规则：
        // 只要IP不在中国，或者命中了港澳台，就直接匹配。
        if (matchOutsideChina && (!region.isChina || region.province in listOf("香港", "澳门", "台湾"))) {
            return true
        }
        // 运营商名称可能同时出现“电信/中国电信”这两种写法，
        // 所以这里先做一次归一化，再比较，避免只修单向前缀导致配置和region写法不一致时漏匹配。
        if (carriers.isNotEmpty() && carriers.none {  region.carrierName == it }) {
            return false
        }
        // 如果规则声明了省份，则当前IP所在省份必须命中其中一个省份，否则排除。
        // 这里用`contains`而不是全等，是为了兼容诸如“广西壮族自治区”这类更长的region文本。
        if (provinces.isNotEmpty() && provinces.none { region.province.contains(it) }) {
            return false
        }
        // 走到这里说明已通过前面的carrier/province过滤。
        // 但完全没写任何匹配条件的节点不应该在这里被视为命中，
        // 它会作为`selectNode()`里的最后fallback节点使用。
        return carriers.isNotEmpty() || provinces.isNotEmpty()
    }

}
