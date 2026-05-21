package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-05-15 19:27
 */


enum class BlockActionForm {BOX,RING}


/**
 * 批量放置方块 x1y1z1~ΔxΔyΔz
 * ring只放边框
 */
@Serializable
data class BlockPlaceBoxQ(
    val blockId: String,
    val state: Map<String,String>,
    val startPos: RBlockPos,
    val deltaPos: RBlockPos,
    val form: BlockActionForm,
    val test: Boolean = false,
)
@Serializable
data class BlockPlaceDiscreteQ(
    val blockId: String,
    val targets: List<Target>,
    val test: Boolean = false,
) {
    @Serializable
    data class Target(
        val pos: RBlockPos,
        val state: Map<String, String> = emptyMap(),
    )
}

// break given poses use player's main hand item
@Serializable
data class BlockBreakBoxQ(
    val startPos: RBlockPos,
    val deltaPos: RBlockPos,
    val form: BlockActionForm,
    val noPickup: Boolean = false,
    val test: Boolean = false,
)
@Serializable
data class BlockBreakDiscreteQ(
    val poses: List<RBlockPos>,
    val noPickup: Boolean = false,
    val test: Boolean = false,
)
@Serializable
data class BlockActionP(
    val count: Int,
    val failures: Map<ActionResult.Err,List<RBlockPos>>,
    val test: Boolean = false
){
    override fun toString()= buildString {
        appendLine("total $count")
        appendLine("test $test")
        appendLine("failures ${failures.values.sumOf { it.size }}")
        appendLine()
        failures.forEach { (result, poses) ->
            appendLine("${result.reason}: ${poses.text}")
        }
    }
}
/*
GET /blocks
search blocks by given ids, in 256x256 player-centered area
128x limit for each given id
 */
@Serializable
data class BlockFindQ(
    val ids: List<String>,
)


/*
128x minecraft:dirt
discrete
-265 102 306

box
-265 102 307 ~ -250 102 307
 */
@Serializable
data class BlockFindP(
    val groups: List<Group>,
) {
    override fun toString(): String {
        return groups.joinToString("\n")
    }

    @Serializable
    data class Group(
        val id: String,
        val count: Int,
        val range: BlockRange
    ) {
        override fun toString() = buildString {
            appendLine("${count}x $id")
            append(range)
        }
    }
}
