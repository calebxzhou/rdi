package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.client.mcp.send
import calebxzhou.rdi.mc.common2.mcp.model.ContainerDropItemQ
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
    override val helpDoc = """
        Read the local player's inventory slots.
        No query params.
        Response includes inventory, armor, and offhand sections. Empty slots are shown so free slots are visible.
        Use slot ids from this response when another API asks for invSlot, toolInvSlot, slotId, or source slot.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.inventory()
    }
}

object InventorySlotHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Read detailed data for one player inventory slot.
        Query params:
        slotId: required integer slot id.
        compart: optional inventory section. Allowed values: INV, ARMOR, OFFHAND. Default is INV.
        Use this when a stack needs exact details beyond item id and count.
    """.trimIndent()

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
    override val helpDoc = """
        Read slots from block containers at given positions.
        Query params:
        poses: required comma separated block positions. Each position uses "x y z" format, such as "-215 140 234, -214 140 234".
        Response groups slots by container position and reports failures for positions that are not readable containers.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val poses = ctx.param("poses").toRBlockPosList()
        if (poses.isEmpty()) throw McpBadRequestError("no poses")
        return ctx.game.send(ContainerSlotListQ(poses))
    }
}
object ContainerSlotMoveHandler : McpTypedHandler {
    override val method = HTTPMethod.POST
    override val helpDoc = """
        Move items between player inventory and block containers.
        Request body is YAML.
        Required fields:
        groups: list of move groups. Each group has from, to, and moves.
        group.from / group.to: container reference. Use {} or omit pos for player inventory; use pos: "x y z" for a block container.
        move.fromSlotId: source slot id.
        move.toSlotId: optional target slot id. Omit to auto-find a target slot.
        move.count: optional amount. Omit to move the whole source stack.
        test: optional boolean. true previews result without changing inventory/container contents.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.send(ctx.ymlBody<ContainerMoveQ>())
    }
}

object ContainerDropItemHandler : McpTypedHandler {
    override val helpDoc = """
        Take items from a player inventory slot or block container slot and spawn them as an item entity at a world position.
        Request body is YAML.
        Required fields:
        source: container slot reference. Use source.slotId for player inventory, or source.pos plus source.slotId for a block container.
        count: positive item amount to take from the source slot.
        x, y, z: world coordinates where the item entity should spawn. These are decimal coordinates, not block position strings.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.send(ctx.ymlBody<ContainerDropItemQ>())
    }
}
