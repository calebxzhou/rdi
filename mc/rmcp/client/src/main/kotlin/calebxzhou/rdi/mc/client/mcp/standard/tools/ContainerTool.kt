package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.ContainerDropItemQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.InventorySlotQ
import kotlinx.serialization.json.JsonObject

object ContainerTool {
    val all: List<StandardMcpTool> = listOf(
        InventoryTool,
        InventorySlotTool,
        ContainerSlotListTool,
        ContainerSlotMoveTool,
        ContainerDropItemTool,
    )
}

private object InventoryTool : StandardMcpTool {
    override val description = """
        Read the local player's inventory slots.
        Response includes inventory, armor, and offhand sections. Empty slots are shown so free slots are visible.
        Use slot ids from this response when another API asks for invSlot, toolInvSlot, slotId, or source slot.
    """.trimIndent()

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return game.inventory().map { inventory ->
            StandardMcpToolResult.text(inventory.toString())
        }
    }
}

private object InventorySlotTool : TypedMcpTool<InventorySlotQ>(
    InventorySlotQ.serializer(),
    InventorySlotQ::class,
) {
    override val description = """
        Read detailed data for one player inventory slot.
        Use this when a stack needs exact details beyond item id and count.
    """.trimIndent()

    override fun callTyped(req: InventorySlotQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            StandardMcpToolResult.text(game.inventorySlot(req).getOrThrow())
        }
    }
}

private object ContainerSlotListTool : TypedMcpTool<ContainerSlotListQ>(
    ContainerSlotListQ.serializer(),
    ContainerSlotListQ::class,
) {
    override val description = """
        Read slots from block containers at given positions.
        Response groups slots by container position and reports failures for positions that are not readable containers.
    """.trimIndent()
}

private object ContainerSlotMoveTool : TypedMcpTool<ContainerMoveQ>(
    ContainerMoveQ.serializer(),
    ContainerMoveQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = """
        Move items between player inventory and block containers.
        Supports dry-run previews before changing inventory or container contents.
    """.trimIndent()
}

private object ContainerDropItemTool : TypedMcpTool<ContainerDropItemQ>(
    ContainerDropItemQ.serializer(),
    ContainerDropItemQ::class,
) {
    override val readOnly = false
    override val destructive = true
    override val description = "Take items from a player inventory or block container slot and spawn them into the world."
}
