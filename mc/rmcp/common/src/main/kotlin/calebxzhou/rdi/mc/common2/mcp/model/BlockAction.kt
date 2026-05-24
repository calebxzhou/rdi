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


@Serializable
data class BlockBreakBoxQ(
    val startPos: RBlockPos,
    val deltaPos: RBlockPos,
    val form: BlockActionForm,
    val toolInvSlot: Int? = null,
    val noPickup: Boolean = false,
    val test: Boolean = false,
)
@Serializable
data class BlockBreakDiscreteQ(
    val poses: List<RBlockPos>,
    val toolInvSlot: Int? = null,
    val noPickup: Boolean = false,
    val test: Boolean = false,
)

@Serializable
data class BlockHarvestResultQ(
    val pos: RBlockPos,
    val invSlot: Int? = null,
)

@Serializable
data class BlockUseItemQ(
    val pos: RBlockPos,
    val invSlot: Int,
    val face: String = "up",
    val hitX: Double = 0.5,
    val hitY: Double = 0.5,
    val hitZ: Double = 0.5,
)

@Serializable
data class BlockUseItemP(
    val pos: RBlockPos,
    val invSlot: Int,
    val itemBefore: String,
    val itemAfter: String,
    val blockBefore: String,
    val blockAfter: String,
    val result: String,
) {
    override fun toString() = buildString {
        appendLine("result $result")
        appendLine("pos $pos")
        appendLine("slot $invSlot")
        appendLine("item $itemBefore -> $itemAfter")
        append("block $blockBefore -> $blockAfter")
    }
}

@Serializable
data class BlockHarvestResultP(
    val pos: RBlockPos,
    val blockId: String,
    val tool: HarvestStack,
    val toolSlot: Int? = null,
    val harvestable: Boolean,
    val drops: List<HarvestStack>,
) {
    override fun toString() = buildString {
        appendLine("block $blockId $pos")
        append("tool ").append(tool)
        if (toolSlot != null) append(" slot ").append(toolSlot)
        appendLine()
        appendLine("harvestable $harvestable")
        append("drops ")
        append(if (drops.isEmpty()) "none" else drops.joinToString(", "))
    }

    @Serializable
    data class HarvestStack(
        val itemId: String,
        val count: Int,
    ) {
        override fun toString() = "${count}x $itemId"
    }
}

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

@Serializable
data class BlockFetchBoxQ(
    val from: RBlockPos,
    val to: RBlockPos,
)

@Serializable
data class BlockFetchBoxP(
    val from: RBlockPos,
    val to: RBlockPos,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val total: Int,
    val palette: List<String>,
    val layers: List<Layer>,
) {
    override fun toString() = buildString {
        appendLine("from $from to $to size ${sizeX}x${sizeY}x${sizeZ} total $total")
        appendLine("order layer=y row=z col=x")
        appendLine("palette")
        palette.forEachIndexed { index, blockId -> appendLine("$index $blockId") }
        layers.forEach { layer ->
            appendLine()
            appendLine("y=${layer.y}")
            append(layer.rows.joinToString("\n"))
        }
    }.trimEnd()

    @Serializable
    data class Layer(
        val y: Int,
        val rows: List<String>,
    )
}


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
