package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class CraftQ(
    @McpParam("Crafting grid rows separated by \"|\". Use letters for ingredients.")
    val pattern: String,
    @McpParam("Map from pattern letter to player inventory slot id, such as {\"A\": 5}.")
    val key: Map<String, Int>,
    @McpParam("Number of crafting batches. Default is 1 and must be positive.", minimum = 1)
    val times: Int = 1,
    @McpParam("true previews consumed slots and result without changing inventory.")
    val test: Boolean = false,
)

@Serializable
data class CraftP(
    // ItemStack.toString
    val result: String,
    val consumed: Map<Int, Int>,
    val recipeId: String,
    val test: Boolean = false,
) {
    override fun toString() = buildString {
        appendLine("crafted $result")
        appendLine("recipe $recipeId")
        appendLine("consumed ${consumed.entries.joinToString(" ") { "${it.key}:${it.value}" }}")
        append("test $test")
    }
}
