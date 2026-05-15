package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpRecipeDetailData(
    val itemId: String,
    val ref: String,
    val recipe: RMcpRecipeData?
)
