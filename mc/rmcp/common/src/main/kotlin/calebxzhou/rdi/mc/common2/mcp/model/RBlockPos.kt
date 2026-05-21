package calebxzhou.rdi.mc.common2.mcp.model

import calebxzhou.rdi.mc.common2.mcp.McpBadArgsError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = RBlockPosSerializer::class)
data class RBlockPos(val x: Int, val y: Int, val z: Int){
    override fun toString() = "$x $y $z"
}
val Iterable<RBlockPos>.text get() = this.joinToString(", ")

fun String.toRBlockPos(): RBlockPos  {
    val parts = trim().split(Regex("\\s+"))
    if (parts.size != 3) {
        throw McpBadArgsError("RBlockPos must be x y z")
    }
    return RBlockPos(
        parts[0].toIntOrNull() ?: throw McpBadArgsError("RBlockPos x is not int"),
        parts[1].toIntOrNull() ?: throw McpBadArgsError("RBlockPos y is not int"),
        parts[2].toIntOrNull() ?: throw McpBadArgsError("RBlockPos z is not int"),
    )
}

fun String.toRBlockPosList(): List<RBlockPos> {
    return split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.toRBlockPos() }
}

//空格分割坐标 123 456 789 -> x=123 y=456 z=789
object RBlockPosSerializer : KSerializer<RBlockPos> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RBlockPos", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RBlockPos) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): RBlockPos {
        return decoder.decodeString().toRBlockPos()
    }
}
