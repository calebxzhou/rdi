package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.json
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.InventoryListP
import calebxzhou.rdi.mc.common2.mcp.model.InventorySlotQ
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeQ
import kotlinx.serialization.encodeToString
import java.util.UUID

interface McpGameInterface {

    fun send(packet: McpC2SNetPacket): Result<String>

    fun inventory(): Result<InventoryListP>

    fun inventorySlot(req: InventorySlotQ): Result<String>

    fun blockFind(req: BlockFindQ): Result<BlockFindP>

    fun recipes(req: RecipeQ): Result<String>

    fun recipeTree(req: RecipeTreeQ): Result<String>
}

inline fun <reified Q : Any> McpGameInterface.send(req: Q): Result<String> {
    return runCatching {
        McpC2SNetPacket(
            reqId = UUID.randomUUID().toString(),
            className = Q::class.java.name,
            reqJson = json.encodeToString(req),
        )
    }.mapCatching { packet ->
        send(packet).getOrThrow()
    }
}
