package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.client.mcp.send
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.InventoryCompart
import calebxzhou.rdi.mc.common2.mcp.model.InventorySlotQ
import calebxzhou.rdi.mc.common2.mcp.model.toRBlockPos
import calebxzhou.rdi.mc.common2.mcp.model.toRBlockPosList
import io.fusionauth.http.HTTPMethod

/**
 * calebxzhou @ 2026-05-19 23:52
 */
private fun McpHttpContext.slotId() = param("slotId").toIntOrNull() ?: throw McpBadRequestError("invalid slot number")
object InventoryHandler : McpTypedHandler {
    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.inventory()
    }
}

object InventorySlotHandler : McpTypedHandler {
    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val compart = ctx.paramNull("compart")
            ?.let { compart ->
                runCatching { InventoryCompart.valueOf(compart) }
                    .getOrElse { throw McpBadRequestError("invalid compart $compart. available: ${InventoryCompart.entries.joinToString()}") }
            } ?: InventoryCompart.INV
        return ctx.game.inventorySlot(InventorySlotQ(
            compart,
            ctx.slotId(),
        ))
    }
}
object ContainerSlotListHandler : McpTypedHandler {
    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val poses = ctx.param("poses").toRBlockPosList()
        if (poses.isEmpty()) throw McpBadRequestError("no poses")
        return ctx.game.send(ContainerSlotListQ(poses))
    }
}
object ContainerSlotMoveHandler : McpTypedHandler {
    override val method = HTTPMethod.POST
    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.send(ctx.ymlBody<ContainerMoveQ>())
    }
}