package calebxzhou.rdi.mc.server.mcpimpl211.handler

import calebxzhou.rdi.mc.common2.mcp.model.ActionResult
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlot
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListP
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.RBlockPos
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities

object ContainerHandler {
    fun slotList(req: ContainerSlotListQ, player: ServerPlayer): Result<ContainerSlotListP> = runCatching {
        val groups = mutableListOf<ContainerSlotListP.Group>()
        val failures = mutableMapOf<ActionResult.Err, MutableList<RBlockPos>>()
        for (pos in req.poses) {
            when (val result = readContainer(player, pos)) {
                is ActionResult.Ok<*> -> groups += result.data as ContainerSlotListP.Group
                is ActionResult.Err -> failures.getOrPut(result) { mutableListOf() } += pos
            }
        }
        ContainerSlotListP(groups, failures)
    }

    private fun readContainer(player: ServerPlayer, pos: RBlockPos): ActionResult<ContainerSlotListP.Group> {
        val level = player.serverLevel()
        val blockPos = BlockPos(pos.x, pos.y, pos.z)
        if (!level.isLoaded(blockPos)) {
            return ActionResult.Err("chunk not loaded")
        }
        val state = level.getBlockState(blockPos)
        val handler = level.getCapability(Capabilities.ItemHandler.BLOCK, blockPos, null)
            ?: return ActionResult.Err("not a container")
        val slots = (0 until handler.slots).map { slotId ->
            slot(slotId, handler.getStackInSlot(slotId))
        }
        return ActionResult.Ok(
            ContainerSlotListP.Group(
                pos = pos,
                blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString(),
                slots = slots,
            )
        )
    }

    private fun slot(id: Int, stack: ItemStack): ContainerSlot {
        return if (stack.isEmpty) {
            ContainerSlot.empty(id)
        } else {
            ContainerSlot(id, BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.count)
        }
    }
}
