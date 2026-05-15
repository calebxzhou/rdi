package calebxzhou.rdi.mc.common2.mcp

import kotlinx.serialization.Serializable
/*
64x minecraft:dirt
-265 102 306
-265 102 307
-265 102 308....
32x minecraft:stone
-265 102 309
-265 102 300
-265 102 312....
 */
@Serializable
data class GetBlocksP(
    val records: List<Record>,
){
    override fun toString(): String {
        return records.joinToString("\n")
    }
    @Serializable
    data class Record(
        val id: String,
        val count: Int,
        val poses: List<RBlockPos>
    ){
        override fun toString() = """
            ${count}x $id
             ${poses.joinToString("\n")}
        """.trimIndent()
    }
}
