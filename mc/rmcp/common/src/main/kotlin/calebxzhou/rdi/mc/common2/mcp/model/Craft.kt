package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class CraftQ(
    val pattern: String,
    val key: Map<String, Int>,
    val times: Int = 1,
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
