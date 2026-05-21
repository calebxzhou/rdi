package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/**
 * calebxzhou @ 2026-05-20 16:33
 */
@Serializable
data class RBlockAABB(val pos1: RBlockPos, val pos2: RBlockPos) {
    override fun toString() = "$pos1 ~ $pos2"
    companion object {
        fun fromDelta(start: RBlockPos, delta: RBlockPos): RBlockAABB {
            val end = RBlockPos(start.x + delta.x, start.y + delta.y, start.z + delta.z)
            return RBlockAABB(start,end)
        }
    }
    val posListBox: List<RBlockPos> get() {
        val positions = mutableListOf<RBlockPos>()
        for (y in min(pos1.y, pos2.y)..max(pos1.y, pos2.y)) {
            for (z in min(pos1.z, pos2.z)..max(pos1.z, pos2.z)) {
                for (x in min(pos1.x, pos2.x)..max(pos1.x, pos2.x)) {
                    positions += RBlockPos(x, y, z)
                }
            }
        }
        return positions
    }

    val posListRing: List<RBlockPos> get() {
        val minX = min(pos1.x, pos2.x)
        val minY = min(pos1.y, pos2.y)
        val minZ = min(pos1.z, pos2.z)
        val maxX = max(pos1.x, pos2.x)
        val maxY = max(pos1.y, pos2.y)
        val maxZ = max(pos1.z, pos2.z)
        val positions = mutableListOf<RBlockPos>()
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    if (isRingBoundary(x, y, z, minX, maxX, minY, maxY, minZ, maxZ)) {
                        positions += RBlockPos(x, y, z)
                    }
                }
            }
        }
        return positions
    }

    private fun isRingBoundary(
        x: Int,
        y: Int,
        z: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
        minZ: Int,
        maxZ: Int,
    ): Boolean {
        val axes = (if (minX == maxX) 0 else 1) + (if (minY == maxY) 0 else 1) + (if (minZ == maxZ) 0 else 1)
        if (axes <= 1) {
            return true
        }
        val boundaryAxes = (if (minX != maxX && (x == minX || x == maxX)) 1 else 0) +
                (if (minY != maxY && (y == minY || y == maxY)) 1 else 0) +
                (if (minZ != maxZ && (z == minZ || z == maxZ)) 1 else 0)
        return boundaryAxes >= axes - 1
    }

}
