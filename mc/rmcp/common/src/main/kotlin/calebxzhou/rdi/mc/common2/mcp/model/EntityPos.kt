package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable
import kotlin.math.floor

@Serializable
data class EntityPos(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val chunkX: Int,
    val chunkZ: Int,
    val sectionY: Int
) {
    constructor(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) : this(
        x,
        y,
        z,
        yaw,
        pitch,
        Math.floorDiv(floor(x).toInt(), 16),
        Math.floorDiv(floor(z).toInt(), 16),
        Math.floorDiv(floor(y).toInt(), 16)
    )
}
