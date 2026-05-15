package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpEntityDetailData(
    val dim: String,
    val uuid: String,
    val type: String,
    val name: String,
    val pos: REntityPosData,
    val runtime: Map<String, Any?>,
    val snbt: String?
)
