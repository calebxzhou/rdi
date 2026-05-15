package calebxzhou.rdi.mc.common2.mcp

import kotlinx.serialization.Serializable

@Serializable
data class RBlockPos(val x: Int, val y: Int, val z: Int){
    override fun toString() = "$x $y $z"
}
