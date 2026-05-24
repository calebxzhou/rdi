package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-05-24 11:33
 */
@Serializable
data class PlayerInfo(
    val dim: String,
    val uuid: String,
    val name: String,
    val pos: EntityPos,
    val health: Float,
    val maxHealth: Float,
    val food: Int,
    val gameMode: String,
) {
    override fun toString() = buildString {
        appendLine("name $name")
        appendLine("uuid $uuid")
        appendLine("dim $dim")
        appendLine("pos ${pos.x} ${pos.y} ${pos.z} yaw=${pos.yaw} pitch=${pos.pitch} chunk=${pos.chunkX},${pos.chunkZ} sectionY=${pos.sectionY}")
        appendLine("health $health/$maxHealth")
        appendLine("food $food")
        append("gameMode $gameMode")
    }
}
