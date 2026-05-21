package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockRange(val discrete: List<RBlockPos>, val box: List<RBlockAABB>) {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (discrete.isNotEmpty()) {
            parts += "discrete\n${discrete.joinToString(" , ")}"
        }
        if (box.isNotEmpty()) {
            parts += "box\n${box.joinToString(" , ")}"
        }
        return parts.joinToString("\n\n")
    }
}
