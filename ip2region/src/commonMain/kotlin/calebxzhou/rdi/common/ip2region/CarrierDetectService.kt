package calebxzhou.rdi.common.ip2region

expect object CarrierDetectService {
    fun detectResult(ipv4: String): Ip2RegionResult
    fun detectCarrierName(ipv4: String): String
}
