package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.mcp.handler.BlockFindHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockFetchBoxHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockBreakBoxHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockBreakDiscreteHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockHarvestResultHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceBoxHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockPlaceDiscreteHandler
import calebxzhou.rdi.mc.client.mcp.handler.BlockUseItemHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerDropItemHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerSlotListHandler
import calebxzhou.rdi.mc.client.mcp.handler.ContainerSlotMoveHandler
import calebxzhou.rdi.mc.client.mcp.handler.CraftHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventoryHandler
import calebxzhou.rdi.mc.client.mcp.handler.InventorySlotHandler
import calebxzhou.rdi.mc.client.mcp.handler.ModInfoHandler
import calebxzhou.rdi.mc.client.mcp.handler.ModListHandler
import calebxzhou.rdi.mc.client.mcp.handler.PlayerHandler
import calebxzhou.rdi.mc.client.mcp.handler.QuestChapterListHandler
import calebxzhou.rdi.mc.client.mcp.handler.QuestOfChapterHandler
import calebxzhou.rdi.mc.client.mcp.handler.RecipeHandler
import calebxzhou.rdi.mc.client.mcp.handler.RecipeTreeHandler
import calebxzhou.rdi.mc.client.mcp.handler.ResIdResolveHandler
import calebxzhou.rdi.mc.client.mcp.handler.ScreenshotHandler
import io.fusionauth.http.HTTPMethod

val HANDLERS = listOf(
    BlockBreakBoxHandler,
    BlockBreakDiscreteHandler,
    BlockFetchBoxHandler,
    BlockFindHandler,
    BlockHarvestResultHandler,
    BlockPlaceBoxHandler,
    BlockPlaceDiscreteHandler,
    BlockUseItemHandler,
    ContainerDropItemHandler,
    ContainerSlotListHandler,
    ContainerSlotMoveHandler,
    CraftHandler,
    InventoryHandler,
    InventorySlotHandler,
    ModInfoHandler,
    ModListHandler,
    PlayerHandler,
    QuestChapterListHandler,
    QuestOfChapterHandler,
    RecipeHandler,
    RecipeTreeHandler,
    ResIdResolveHandler,
    ScreenshotHandler,
)

interface McpTypedHandler {
    val method: HTTPMethod get() = HTTPMethod.POST
    val helpDoc: String
        get() = "help doc unavailable"

    fun handle(ctx: McpHttpContext): Result<Any?>
}
