package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpCraftParallelRequest(
    val crafts: List<Craft>,
    val dryRun: Boolean
) {
    @JvmRecord
    data class Craft(
        val slots: Map<String, Int>,
        val shape: String,
        val outputSlot: Int,
        val times: Int?
    )
}
