package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class ResIdResolveQ(
    @McpParam("Search text, such as oak plank, create seat, or wood slab.")
    val text: String,
    @McpParam(
        "Optional resource kind filters.",
        enumValues = ["item", "block", "item_tag", "block_tag", "tag", "tags"],
    )
    val kinds: List<String> = emptyList(),
)

@Serializable
data class ResourceResolveP(
    val matches: List<ResourceMatch>,
) {
    override fun toString() = buildString {
        appendLine("kind id name")
        matches.forEach { appendLine(it) }
    }.trimEnd()
}

@Serializable
data class ResourceMatch(
    val kind: String,
    val id: String,
    val name: String,
) {
    override fun toString() = "$kind $id $name"
}
