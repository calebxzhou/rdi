package calebxzhou.rdi.common.model.world

data class RChunkPos(
    val x: Short, val z:Short
){
    val packed = x.toInt() and 0xFFFF or ((z.toInt() and 0xFFFF) shl 16)
    constructor(packed: Int): this(packed.toShort(),(packed shr 16).toShort())
}
