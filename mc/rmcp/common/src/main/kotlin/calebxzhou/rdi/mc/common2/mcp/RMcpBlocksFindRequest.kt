package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpBlocksFindRequest(
    val id: String,
    val ids: List<String>,
    val chunkRadius: Int,
    val sectionRadius: Int,
    val scanMode: String,
    val limit: Int,
    val includeState: Boolean?
)
