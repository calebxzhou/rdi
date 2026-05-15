package calebxzhou.rdi.mc.common2.mcp

@JvmRecord
data class RMcpRecipeData(
    val id: String,
    val source: String,
    val type: String,
    val category: String,
    val title: String,
    val runtimeClass: String,
    val inputs: List<IngredientSlot>,
    val outputs: List<IngredientSlot>,
    val catalysts: List<IngredientSlot>,
    val renderOnly: List<IngredientSlot>,
    val extra: Map<String, Any?>?
) {
    @JvmRecord
    data class IngredientSlot(
        val role: String,
        val items: List<Item>,
        val fluids: List<Fluid>,
        val tags: List<ItemTag>
    ) {
        constructor(role: String, items: List<Item>, fluids: List<Fluid>) : this(
            role,
            items,
            fluids,
            mutableListOf<ItemTag>()
        )
    }

    @JvmRecord
    data class ItemTag(
        val id: String,
        val count: Int,
        val candidateCount: Int,
        val examples: List<Item>,
        val source: String?
    )

    @JvmRecord
    data class Item(
        val id: String,
        val langKey: String,
        val count: Int,
        val snbt: String?
    )

    @JvmRecord
    data class Fluid(
        val id: String,
        val name: String,
        val amount: Long
    )
}
