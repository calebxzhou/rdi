package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpModData(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val dependencies: List<Dependency>
) {
    @JvmRecord
    data class Dependency(
        val id: String,
        val versionRange: String,
        val type: String,
        val ordering: String,
        val side: String?
    )
}
