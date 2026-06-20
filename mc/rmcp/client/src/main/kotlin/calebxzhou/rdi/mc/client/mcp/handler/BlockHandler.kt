package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.client.mcp.send
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockHarvestResultQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockUseItemQ
import calebxzhou.rdi.mc.common2.mcp.model.toRBlockPos
import io.fusionauth.http.HTTPMethod

/**
 * calebxzhou @ 2026-05-19 17:46
 */
object BlockPlaceBoxHandler : McpTypedHandler {
    override val helpDoc = """
        Place many blocks in a rectangular area.
        Request body is YAML.
        Required fields:
        blockId: exact block id, such as minecraft:dirt.
        state: block state properties map. Use {} when no state is needed.
        startPos: start block position in "x y z" format, such as "-215 140 234".
        deltaPos: offset from startPos to the opposite corner in "dx dy dz" format. "0 0 0" means one block.
        form: BOX fills the whole area, RING places only the border.
        test: optional boolean. true previews result without changing the world.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockPlaceBoxQ>()
        return ctx.game.send(req)
    }
}
object BlockPlaceDiscreteHandler : McpTypedHandler {
    override val helpDoc = """
        Place blocks at explicit positions.
        Request body is YAML.
        Required fields:
        blockId: exact block id, such as minecraft:dirt.
        targets: list of target entries. Each target has pos and optional state.
        target.pos: block position in "x y z" format, such as "-215 140 234".
        target.state: block state properties map. Use {} or omit when no state is needed.
        test: optional boolean. true previews result without changing the world.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockPlaceDiscreteQ>()
        return ctx.game.send(req)
    }
}

object BlockBreakBoxHandler : McpTypedHandler {
    override val method = HTTPMethod.POST
    override val helpDoc = """
        Break many blocks in a rectangular area.
        Request body is YAML.
        Required fields:
        startPos: start block position in "x y z" format, such as "-215 140 234".
        deltaPos: offset from startPos to the opposite corner in "dx dy dz" format. "0 0 0" means one block.
        form: BOX breaks the whole area, RING breaks only the border.
        Optional fields:
        toolInvSlot: player inventory slot id used as tool. Omit to use server/default behavior.
        noPickup: true means do not pick up drops.
        test: true previews result without changing the world.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockBreakBoxQ>()
        return ctx.game.send(req)
    }
}
object BlockBreakDiscreteHandler : McpTypedHandler {
    override val method = HTTPMethod.POST
    override val helpDoc = """
        Break blocks at explicit positions.
        Request body is YAML.
        Required fields:
        poses: list of block positions. Each position uses "x y z" format, such as "-215 140 234". Seperate by comma, such as "-215 140 234,215 140 234,215 140 235"
        Optional fields:
        toolInvSlot: player inventory slot id used as tool. Omit to use server/default behavior.
        noPickup: true means do not pick up drops.
        test: true previews result without changing the world.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockBreakDiscreteQ>()
        return ctx.game.send(req)
    }
}
object BlockFindHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Find nearby blocks by exact block ids.
        Query params:
        ids: required space separated block ids, such as "minecraft:oak_log minecraft:chest".
        Use res-id-resolve first when the user gives fuzzy names or localized names.
        Search is player-centered and returns grouped positions/ranges for matching blocks.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val ids = ctx.param("ids").trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return ctx.game.blockFind(BlockFindQ(ids))
    }
}

object BlockFetchBoxHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Fetch block ids in an AABB range as palette layers.
        Query params:
        from: required first corner block position in "x y z" format, such as "-215 140 234".
        to: required opposite corner block position in "x y z" format.
        Max 256 blocks inclusive.
        Response uses palette indexes. Layer order is y, row is z, column is x.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val from = ctx.param("from").toRBlockPos()
        val to = ctx.param("to").toRBlockPos()
        return ctx.game.blockFetchBox(BlockFetchBoxQ(from, to))
    }
}

object BlockHarvestResultHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Preview what items a block would drop if harvested with a tool from a player inventory slot.
        Query params:
        pos: required block position in "x y z" format, such as "-215 140 234".
        invSlot: required player inventory slot id used as the harvesting tool.
        This does not break the block. It returns harvestability and expected drops for the given slot.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val pos = ctx.param("pos").toRBlockPos()
        val invSlot = ctx.param("invSlot").trim().toIntOrNull() ?: throw McpBadRequestError("invSlot must be integer")
        return ctx.game.send(BlockHarvestResultQ(pos, invSlot))
    }
}

object BlockUseItemHandler : McpTypedHandler {
    override val helpDoc = """
        Use an item from the player's inventory on a block.
        Request body is YAML.
        Required fields:
        pos: target block position in "x y z" format, such as "-215 140 234".
        invSlot: player inventory slot id containing the item to use.
        Optional fields:
        face: clicked face. Common values: up, down, north, south, east, west. Default is up.
        hitX, hitY, hitZ: hit location inside the block from 0.0 to 1.0. Default is 0.5 for each axis.
        This performs normal Minecraft item-on-block interaction.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.send(ctx.ymlBody<BlockUseItemQ>())
    }
}
