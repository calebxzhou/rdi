package calebxzhou.rdi.common.anvilrw.util

import calebxzhou.rdi.common.anvilrw.core.Chunk
import net.benwoodworth.knbt.nbtByte
import net.benwoodworth.knbt.nbtCompound
import net.benwoodworth.knbt.nbtInt
import net.benwoodworth.knbt.nbtList
import net.benwoodworth.knbt.nbtLongArray
import net.benwoodworth.knbt.nbtString
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max

data class ChunkSurfaceSection(
    val sectionY: Int,
    val palette: List<String>,
    val data: LongArray?
)

fun Chunk.buildSurfaceSections(defaultBlockId: String = "minecraft:air"): List<ChunkSurfaceSection> {
    val root = getNbtData() ?: return emptyList()
    val sectionTags = root["sections"]?.nbtList ?: return emptyList()
    return sectionTags.mapNotNull { sectionTag ->
        val section = sectionTag.nbtCompound ?: return@mapNotNull null
        val sectionY = section["Y"]?.nbtByte?.value?.toInt()
            ?: section["Y"]?.nbtInt?.value
            ?: return@mapNotNull null
        val blockStates = section["block_states"]?.nbtCompound ?: return@mapNotNull null
        val palette = blockStates["palette"]?.nbtList
            ?.mapNotNull { it.nbtCompound["Name"]?.nbtString?.value }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(defaultBlockId)
        ChunkSurfaceSection(
            sectionY = sectionY,
            palette = palette,
            data = blockStates["data"]?.nbtLongArray?.toLongArray()
        )
    }.sortedByDescending { it.sectionY }
}

fun sampleSurfaceFromSections(
    localX: Int,
    localZ: Int,
    sections: List<ChunkSurfaceSection>,
    defaultBlockId: String = "minecraft:air"
): String {
    for (section in sections) {
        for (localY in 15 downTo 0) {
            val paletteIndex = decodePaletteIndex(localX, localY, localZ, section.palette.size, section.data)
            val blockName = section.palette.getOrElse(paletteIndex) { defaultBlockId }
            if (!isAirBlock(blockName)) {
                return blockName
            }
        }
    }
    return defaultBlockId
}

private fun decodePaletteIndex(
    localX: Int,
    localY: Int,
    localZ: Int,
    paletteSize: Int,
    data: LongArray?
): Int {
    if (paletteSize <= 1 || data == null || data.isEmpty()) return 0
    val bitsPerBlock = max(4, ceil(log2(paletteSize.toDouble())).toInt())
    val entriesPerLong = 64 / bitsPerBlock
    if (entriesPerLong <= 0) return 0

    val blockIndex = ((localY and 0xF) shl 8) or ((localZ and 0xF) shl 4) or (localX and 0xF)
    val longIndex = blockIndex / entriesPerLong
    if (longIndex >= data.size) return 0
    val bitOffset = (blockIndex % entriesPerLong) * bitsPerBlock
    val mask = (1L shl bitsPerBlock) - 1L
    val value = (data[longIndex] ushr bitOffset) and mask
    return value.toInt().coerceIn(0, paletteSize - 1)
}

private fun isAirBlock(name: String): Boolean {
    val id = name.removePrefix("minecraft:")
    return id == "air" || id == "cave_air" || id == "void_air" || id == "barrier" || id == "structure_void"
}
