package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpHarvestToolData(
    val stateSource: String,
    val block: Block,
    val scenarios: List<ToolScenario>,
    val notes: List<String>
) {
    @JvmRecord
    data class Block(
        val id: String,
        val state: String,
        val pos: RBlockPos,
        val requiresCorrectToolForDrops: Boolean,
        val mineableWith: List<String>,
        val minimumTier: String?
    )

    @JvmRecord
    data class Tool(
        val id: String,
        val category: String,
        val tier: String,
        val enchantments: Map<String, Int>?
    )

    @JvmRecord
    data class Drop(val id: String, val countMin: Int, val countMax: Int, val snbt: String?)

    @JvmRecord
    data class ToolScenario(
        val tool: Tool,
        val correctToolForDrops: Boolean,
        val harvestable: Boolean,
        val drops: List<Drop>
    )
}
