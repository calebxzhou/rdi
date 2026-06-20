package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class ModInfoQ(
    @McpParam("Exact mod id, such as minecraft, neoforge, create, or jei.")
    val id: String,
)

@Serializable
data class ModInfo(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val dependencies: List<ModDependency>,
) {
    override fun toString() = buildString {
        appendLine("id $id")
        appendLine("name $name")
        appendLine("version $version")
        appendLine("description ${description.lineSequence().joinToString(" ")}")
        append("dependencies ")
        if (dependencies.isEmpty()) {
            append("none")
        } else {
            append(dependencies.joinToString("; "))
        }
    }
}

@Serializable
data class ModDependency(
    val id: String,
    val versionRange: String,
    val type: String,
    val ordering: String,
    val side: String,
) {
    override fun toString(): String {
        return "$id[type=$type,ordering=$ordering,side=$side,range=$versionRange]"
    }
}
