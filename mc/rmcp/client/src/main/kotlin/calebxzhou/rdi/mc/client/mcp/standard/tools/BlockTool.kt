package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockHarvestResultQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockUseItemQ

object BlockTool {
    val all: List<StandardMcpTool> = listOf(
        BlockPlaceBoxTool,
        BlockPlaceDiscreteTool,
        BlockBreakBoxTool,
        BlockBreakDiscreteTool,
        BlockFindTool,
        BlockFetchBoxTool,
        BlockHarvestResultTool,
        BlockUseItemTool,
    )
}

private object BlockPlaceBoxTool : TypedMcpTool<BlockPlaceBoxQ>(
    BlockPlaceBoxQ.serializer(),
    BlockPlaceBoxQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Place one block type across a box or rectangular border in the world."
}

private object BlockPlaceDiscreteTool : TypedMcpTool<BlockPlaceDiscreteQ>(
    BlockPlaceDiscreteQ.serializer(),
    BlockPlaceDiscreteQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Place one block type at explicit world positions."
}

private object BlockBreakBoxTool : TypedMcpTool<BlockBreakBoxQ>(
    BlockBreakBoxQ.serializer(),
    BlockBreakBoxQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Break blocks across a box or rectangular border in the world."
}

private object BlockBreakDiscreteTool : TypedMcpTool<BlockBreakDiscreteQ>(
    BlockBreakDiscreteQ.serializer(),
    BlockBreakDiscreteQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Break blocks at explicit world positions."
}

private object BlockFindTool : TypedMcpTool<BlockFindQ>(
    BlockFindQ.serializer(),
    BlockFindQ::class,
) {
    override val description = """
        Find nearby blocks by exact block ids.
        Use res_id_resolve first when the user gives fuzzy names or localized names.
        Search is player-centered and returns grouped positions/ranges for matching blocks.
    """.trimIndent()

    override fun callTyped(req: BlockFindQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val result = game.blockFind(req).getOrThrow()
            StandardMcpToolResult.text(result.toString())
        }
    }
}

private object BlockFetchBoxTool : TypedMcpTool<BlockFetchBoxQ>(
    BlockFetchBoxQ.serializer(),
    BlockFetchBoxQ::class,
) {
    override val description = """
        Fetch block ids in an AABB range as palette layers.
        Response uses palette indexes. Layer order is y, row is z, column is x.
    """.trimIndent()

    override fun callTyped(req: BlockFetchBoxQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val result = game.blockFetchBox(req).getOrThrow()
            StandardMcpToolResult.text(result.toString())
        }
    }
}

private object BlockHarvestResultTool : TypedMcpTool<BlockHarvestResultQ>(
    BlockHarvestResultQ.serializer(),
    BlockHarvestResultQ::class,
) {
    override val description = """
        Preview what items a block would drop if harvested with a tool from a player inventory slot.
        This does not break the block. It returns harvestability and expected drops for the given slot.
    """.trimIndent()
}

private object BlockUseItemTool : TypedMcpTool<BlockUseItemQ>(
    BlockUseItemQ.serializer(),
    BlockUseItemQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Use an item from the player's inventory on a block through normal Minecraft interaction."
}
