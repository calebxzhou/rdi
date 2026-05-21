package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.mcp.handler.BlockFindHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceBoxHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceDiscreteHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerSlotListHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventoryHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventorySlotHandler
import io.fusionauth.http.HTTPMethod

val HANDLERS = listOf(
    BlockFindHandler,
    BlockPlaceBoxHandler,
    BlockPlaceDiscreteHandler,
    ContainerSlotListHandler,
    InventoryHandler,
    InventorySlotHandler,
)

interface McpTypedHandler {
    val method: HTTPMethod get() = HTTPMethod.POST

    fun handle(ctx: McpHttpContext): Result<Any?>
}
