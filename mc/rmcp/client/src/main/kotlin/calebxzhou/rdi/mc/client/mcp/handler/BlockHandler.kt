package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.client.mcp.send
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import io.fusionauth.http.HTTPMethod

/**
 * calebxzhou @ 2026-05-19 17:46
 */
object BlockPlaceBoxHandler : McpTypedHandler {
    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockPlaceBoxQ>()
        return ctx.game.send(req)
    }
}
object BlockBreakBoxHandler : McpTypedHandler {
    override val method = HTTPMethod.POST
    override fun handle(ctx: McpHttpContext): Result<Any?> {
        // break given poses use player's main hand item, auto pick droppings to inventory
        val req = ctx.ymlBody<BlockBreakBoxHandler>()
        //todo
        return ctx.game.send(req)
    }
}

object BlockFindHandler : McpTypedHandler {
    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val ids = ctx.param("ids").trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return ctx.game.blockFind(BlockFindQ(ids))
    }
}

object BlockPlaceDiscreteHandler : McpTypedHandler {
    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<BlockPlaceDiscreteQ>()
        return ctx.game.send(req)
    }
}

