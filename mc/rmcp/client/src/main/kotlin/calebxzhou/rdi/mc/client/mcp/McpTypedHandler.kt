package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.mcp.handler.BlockFindHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceBoxHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceDiscreteHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerSlotListHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerSlotMoveHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventoryHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventorySlotHandler
import calebxzhou.rdi.mc.client.mcp.handler.RecipeHandler
import calebxzhou.rdi.mc.client.mcp.handler.RecipeTreeHandler
import calebxzhou.rdi.mc.client.mcp.handler.ResourceResolveHandler
import io.fusionauth.http.HTTPMethod

val HANDLERS = listOf(
    BlockFindHandler,
    BlockPlaceBoxHandler,
    BlockPlaceDiscreteHandler,
    ContainerSlotListHandler,
    ContainerSlotMoveHandler,
    InventoryHandler,
    InventorySlotHandler,
    RecipeHandler,
    RecipeTreeHandler,
    ResourceResolveHandler,
)

interface McpTypedHandler {
    val method: HTTPMethod get() = HTTPMethod.POST

    fun handle(ctx: McpHttpContext): Result<Any?>
}
