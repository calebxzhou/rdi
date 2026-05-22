package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

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
