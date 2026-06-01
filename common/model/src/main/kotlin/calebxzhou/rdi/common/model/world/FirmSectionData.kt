package calebxzhou.rdi.common.model.world

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.benwoodworth.knbt.NbtCompound
import org.bson.types.ObjectId

/**
 * calebxzhou @ 2025-04-16 23:01
 */
@Serializable
data class FirmSectionData(
    @Contextual
    val _id: ObjectId = ObjectId(),
    @Contextual
    val roomId: ObjectId,
    val blockStates: List<RBlockState> = List(4096) { RBlockState("minecraft:air") },
    val blockEntities: List<NbtCompound?> = List(4096) { null },
) {
    //fun getBlock(sx:Int,sy:Int,sz:Int) = blocks[encodeYZX(sx,sy,sz)]

    companion object {
        //本地方块坐标 xyz0~15
        fun decodeYZX(encoded: Int): RBlockPos {
            val x = encoded and 0xF
            val z = (encoded shr 4) and 0xF
            val y = (encoded shr 8) and 0xF
            return RBlockPos(x, y, z)
        }

        fun encodeYZX(x: Int, y: Int, z: Int): Short {
            if (!(x in 0..15 && y in 0..15 && z in 0..15)) {
                throw IllegalArgumentException("xyz坐标在section内只能0~15！")
            }
            return (y shl 8 or (z shl 4) or x).toShort()
        }
    }

}