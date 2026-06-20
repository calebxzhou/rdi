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
    @McpParam("Exact block id, such as minecraft:dirt.")
    val blockId: String,
    @McpParam("Block state properties. Use {} when no state is needed.")
    val state: Map<String,String>,
    @McpParam("Start block position in \"x y z\" format.")
    val startPos: RBlockPos,
    @McpParam("Offset from startPos to the opposite corner in \"dx dy dz\" format.")
    val deltaPos: RBlockPos,
    @McpParam("BOX fills the whole area, RING places only the border.")
    val form: BlockActionForm,
    @McpParam("true previews result without changing the world.")
    val test: Boolean = false,
)
@Serializable
data class BlockPlaceDiscreteQ(
    @McpParam("Exact block id, such as minecraft:dirt.")
    val blockId: String,
    @McpParam("Target entries. Each target has pos and optional state.", minItems = 1)
    val targets: List<Target>,
    @McpParam("true previews result without changing the world.")
    val test: Boolean = false,
) {
    @Serializable
    data class Target(
        @McpParam("Block position in \"x y z\" format.")
        val pos: RBlockPos,
        @McpParam("Block state properties. Use {} or omit when no state is needed.")
        val state: Map<String, String> = emptyMap(),
    )
}


@Serializable
data class BlockBreakBoxQ(
    @McpParam("Start block position in \"x y z\" format.")
    val startPos: RBlockPos,
    @McpParam("Offset from startPos to the opposite corner in \"dx dy dz\" format.")
    val deltaPos: RBlockPos,
    @McpParam("BOX breaks the whole area, RING breaks only the border.")
    val form: BlockActionForm,
    @McpParam("Player inventory slot id used as tool. Omit to use server/default behavior.", minimum = 0)
    val toolInvSlot: Int? = null,
    @McpParam("true means do not pick up drops.")
    val noPickup: Boolean = false,
    @McpParam("true previews result without changing the world.")
    val test: Boolean = false,
)
@Serializable
data class BlockBreakDiscreteQ(
    @McpParam("Block positions. Each position uses \"x y z\" format.", minItems = 1)
    val poses: List<RBlockPos>,
    @McpParam("Player inventory slot id used as tool. Omit to use server/default behavior.", minimum = 0)
    val toolInvSlot: Int? = null,
    @McpParam("true means do not pick up drops.")
    val noPickup: Boolean = false,
    @McpParam("true previews result without changing the world.")
    val test: Boolean = false,
)

@Serializable
data class BlockHarvestResultQ(
    @McpParam("Block position in \"x y z\" format.")
    val pos: RBlockPos,
    @McpParam("Player inventory slot id used as the harvesting tool.", minimum = 0)
    val invSlot: Int,
)

@Serializable
data class BlockUseItemQ(
    @McpParam("Target block position in \"x y z\" format.")
    val pos: RBlockPos,
    @McpParam("Player inventory slot id containing the item to use.", minimum = 0)
    val invSlot: Int,
    @McpParam("Clicked face. Default is up.", enumValues = ["up", "down", "north", "south", "east", "west"])
    val face: String = "up",
    @McpParam("Hit location X inside the block from 0.0 to 1.0. Default is 0.5.")
    val hitX: Double = 0.5,
    @McpParam("Hit location Y inside the block from 0.0 to 1.0. Default is 0.5.")
    val hitY: Double = 0.5,
    @McpParam("Hit location Z inside the block from 0.0 to 1.0. Default is 0.5.")
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
    val toolSlot: Int,
    val harvestable: Boolean,
    val drops: List<HarvestStack>,
) {
    override fun toString() = buildString {
        appendLine("block $blockId $pos")
        append("tool ").append(tool)
        append(" slot ").append(toolSlot)
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
    @McpParam("Exact block ids, such as minecraft:oak_log and minecraft:chest.", minItems = 1)
    val ids: List<String>,
)

@Serializable
data class BlockFetchBoxQ(
    @McpParam("First corner block position in \"x y z\" format.")
    val from: RBlockPos,
    @McpParam("Opposite corner block position in \"x y z\" format.")
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
