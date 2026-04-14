package calebxzhou.rdi.common.ip2region

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

    val isCU
        get() = carrierName == "联通"

    val isCM
        get() = carrierName == "移动"

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
                isp = parts.getOrNull(3).normalizePart(),
                countryCode = parts.getOrNull(4).normalizePart(),
            )
        }

        private fun String?.normalizePart() =
            this?.trim()
                ?.takeUnless { it.isEmpty() || it == EMPTY }
                .orEmpty()
    }
}
