package calebxzhou.rdi.mc.server.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBadSlotError
import calebxzhou.rdi.mc.common2.mcp.McpContainerError
import calebxzhou.rdi.mc.common2.mcp.model.ActionResult
import calebxzhou.rdi.mc.common2.mcp.model.ContainerDropItemP
import calebxzhou.rdi.mc.common2.mcp.model.ContainerDropItemQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveP
import calebxzhou.rdi.mc.common2.mcp.model.ContainerMoveQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerRef
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlot
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListP
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlotListQ
import calebxzhou.rdi.mc.common2.mcp.model.RBlockPos
import net.minecraft.block.Block
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.IInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import kotlin.math.floor

object ContainerMcpHandler1710 {
    fun slotList(req: ContainerSlotListQ, player: EntityPlayerMP): Result<ContainerSlotListP> = runCatching {
        val groups = mutableListOf<ContainerSlotListP.Group>()
        val failures = linkedMapOf<ActionResult.Err, MutableList<RBlockPos>>()
        val world = player.worldObj

        for (pos in req.poses) {
            if (!world.blockExists(pos.x, pos.y, pos.z)) {
                failures.add(pos, "block is not loaded")
                continue
            }

            val tile = world.getTileEntity(pos.x, pos.y, pos.z)
            val inventory = tile as? IInventory
            if (inventory == null) {
                failures.add(pos, "block is not inventory")
                continue
            }

            groups += ContainerSlotListP.Group(
                pos = pos,
                blockId = blockId(world.getBlock(pos.x, pos.y, pos.z)),
                slots = (0 until inventory.getSizeInventory()).map { slotId ->
                    slot(slotId, inventory.getStackInSlot(slotId))
                },
            )
        }

        ContainerSlotListP(groups, failures)
    }

    fun move(req: ContainerMoveQ, player: EntityPlayerMP): Result<ContainerMoveP> = runCatching {
        val failures = linkedMapOf<ActionResult.Err, MutableList<ContainerMoveP.Failure>>()
        var count = 0
        for ((groupId, group) in req.groups.withIndex()) {
            val from = resolveContainer(group.from, player)
            val to = resolveContainer(group.to, player)
            for ((moveId, move) in group.moves.withIndex()) {
                val result = from as? ActionResult.Err ?: (to as? ActionResult.Err ?: run {
                    val fromContainer = (from as ActionResult.Ok<*>).data as LegacyContainer
                    val resolvedToContainer = (to as ActionResult.Ok<*>).data as LegacyContainer
                    val toContainer = if (fromContainer.key == resolvedToContainer.key) {
                        fromContainer
                    } else {
                        resolvedToContainer
                    }
                    moveOne(
                        from = fromContainer,
                        to = toContainer,
                        move = move,
                        test = req.test,
                    )
                })
                when (result) {
                    is ActionResult.Ok<*> -> count += result.data as Int
                    is ActionResult.Err -> failures.getOrPut(result) { mutableListOf() } += ContainerMoveP.Failure(
                        groupId = groupId,
                        moveId = moveId,
                        fromSlotId = move.fromSlotId,
                        toSlotId = move.toSlotId,
                        count = move.count,
                    )
                }
            }
        }
        ContainerMoveP(count, failures, req.test)
    }

    fun dropItem(req: ContainerDropItemQ, player: EntityPlayerMP): Result<ContainerDropItemP> = runCatching {
        if (req.count <= 0) {
            throw McpContainerError("bad count ${req.count}")
        }
        val world = player.worldObj
        if (!world.blockExists(floorBlock(req.x), floorBlock(req.y), floorBlock(req.z))) {
            throw McpContainerError("spawn position is not loaded")
        }
        val source = when (val resolved = resolveContainer(ContainerRef(req.source.pos), player)) {
            is ActionResult.Ok<*> -> resolved.data as LegacyContainer
            is ActionResult.Err -> throw McpContainerError(resolved.reason)
        }
        val slotId = req.source.slotId
        if (!source.validSlot(slotId)) {
            throw McpBadSlotError()
        }
        val sourceStack = source.slot(slotId) ?: throw McpContainerError("source empty")
        if (sourceStack.stackSize <= 0) {
            throw McpContainerError("source empty")
        }
        if (sourceStack.stackSize < req.count) {
            throw McpContainerError("source only has ${sourceStack.stackSize}, requested ${req.count}")
        }
        val dropped = sourceStack.copy().also { it.stackSize = req.count }
        val itemEntity = EntityItem(world, req.x, req.y, req.z, dropped)
        itemEntity.motionX = 0.0
        itemEntity.motionY = 0.0
        itemEntity.motionZ = 0.0
        if (!world.spawnEntityInWorld(itemEntity)) {
            throw McpContainerError("spawn item entity failed")
        }
        sourceStack.splitStack(req.count)
        if (sourceStack.stackSize <= 0) {
            source.setSlot(slotId, null)
        }
        source.markDirty()
        ContainerDropItemP(
            entityId = itemEntity.entityId,
            item = ContainerDropItemP.Stack(itemId(dropped), dropped.stackSize),
        )
    }

    private fun MutableMap<ActionResult.Err, MutableList<RBlockPos>>.add(pos: RBlockPos, reason: String) {
        getOrPut(ActionResult.Err(reason)) { mutableListOf() } += pos
    }

    private fun resolveContainer(ref: ContainerRef, player: EntityPlayerMP): ActionResult<LegacyContainer> {
        val pos = ref.pos ?: return ActionResult.Ok(
            LegacyContainer(
                key = "inventory",
                slots = player.inventory.mainInventory,
                stackLimit = player.inventory.inventoryStackLimit,
                canInsert = { _, _ -> true },
                markDirty = { player.inventory.markDirty() },
            )
        )
        val world = player.worldObj
        if (!world.blockExists(pos.x, pos.y, pos.z)) {
            return ActionResult.Err("block is not loaded")
        }
        val inventory = world.getTileEntity(pos.x, pos.y, pos.z) as? IInventory
            ?: return ActionResult.Err("block is not inventory")
        return ActionResult.Ok(
            LegacyContainer(
                key = "block:$pos",
                slots = Array(inventory.getSizeInventory()) { slotId -> inventory.getStackInSlot(slotId) },
                stackLimit = inventory.inventoryStackLimit,
                canInsert = { slotId, stack -> inventory.isItemValidForSlot(slotId, stack) },
                markDirty = {
                    for (slotId in 0 until inventory.getSizeInventory()) {
                        inventory.setInventorySlotContents(slotId, it[slotId])
                    }
                    inventory.markDirty()
                },
            )
        )
    }

    private fun moveOne(
        from: LegacyContainer,
        to: LegacyContainer,
        move: ContainerMoveQ.Move,
        test: Boolean,
    ): ActionResult<Int> {
        val toSlotId = move.toSlotId
        if (!from.validSlot(move.fromSlotId)) {
            return ActionResult.Err("bad source slot")
        }
        if (toSlotId != null && !to.validSlot(toSlotId)) {
            return ActionResult.Err("bad target slot")
        }
        if (toSlotId != null && from.key == to.key && move.fromSlotId == toSlotId) {
            return ActionResult.Err("same slot")
        }
        val sourceStack = from.slot(move.fromSlotId)
        if (sourceStack == null || sourceStack.stackSize <= 0) {
            return ActionResult.Err("source empty")
        }
        val requestCount = move.count ?: sourceStack.stackSize
        if (requestCount <= 0) {
            return ActionResult.Err("bad count")
        }
        val extracted = sourceStack.copy().also { it.stackSize = minOf(requestCount, sourceStack.stackSize) }
        val plan = insertionPlan(
            target = to,
            targetSlotId = toSlotId,
            stack = extracted,
            excludedSlotId = if (from.key == to.key) move.fromSlotId else null,
        )
        if (plan.count <= 0) {
            return ActionResult.Err("target full")
        }
        if (test) {
            return ActionResult.Ok(plan.count)
        }
        val moved = sourceStack.splitStack(plan.count)
        if (sourceStack.stackSize <= 0) {
            from.setSlot(move.fromSlotId, null)
        }
        insertByPlan(to, moved, plan)
        from.markDirty()
        to.markDirty()
        return ActionResult.Ok(plan.count)
    }

    private fun insertionPlan(
        target: LegacyContainer,
        targetSlotId: Int?,
        stack: ItemStack,
        excludedSlotId: Int?,
    ): InsertPlan {
        val steps = mutableListOf<InsertStep>()
        var remaining = stack.stackSize
        val slotIds = targetSlotId?.let(::listOf) ?: target.autoTargetSlotIds(stack, excludedSlotId)
        for (slotId in slotIds) {
            if (remaining <= 0) break
            val moved = target.insertableCount(slotId, stack, remaining)
            if (moved > 0) {
                steps += InsertStep(slotId, moved)
                remaining -= moved
            }
        }
        return InsertPlan(stack.stackSize - remaining, steps)
    }

    private fun LegacyContainer.autoTargetSlotIds(stack: ItemStack, excludedSlotId: Int?): List<Int> {
        val slotIds = slotIds().filter { it != excludedSlotId }
        val mergeSlots = slotIds.filter { slotId ->
            val targetStack = slot(slotId)
            targetStack != null && targetStack.stackSize > 0 && targetStack.canMerge(stack)
        }
        val emptySlots = slotIds.filter { (slot(it)?.stackSize ?: 0) <= 0 }
        return mergeSlots + emptySlots
    }

    private fun LegacyContainer.insertableCount(slotId: Int, stack: ItemStack, maxCount: Int): Int {
        if (!canInsert(slotId, stack)) {
            return 0
        }
        val targetStack = slot(slotId)
        if (targetStack == null || targetStack.stackSize <= 0) {
            return minOf(maxCount, stack.maxStackSize, stackLimit, stack.stackSize)
        }
        if (!targetStack.canMerge(stack)) {
            return 0
        }
        return (minOf(maxCount, targetStack.maxStackSize, stackLimit) - targetStack.stackSize).coerceAtLeast(0)
    }

    private fun insertByPlan(target: LegacyContainer, stack: ItemStack, plan: InsertPlan) {
        var remaining = stack.stackSize
        for (step in plan.steps) {
            if (remaining <= 0) break
            val count = minOf(step.count, remaining)
            val targetStack = target.slot(step.slotId)
            if (targetStack == null || targetStack.stackSize <= 0) {
                target.setSlot(step.slotId, stack.copy().also { it.stackSize = count })
            } else {
                targetStack.stackSize += count
            }
            remaining -= count
        }
    }

    private fun slot(id: Int, stack: ItemStack?): ContainerSlot {
        return stack?.takeIf { it.stackSize > 0 }?.let {
            ContainerSlot(id, itemId(it), it.stackSize)
        } ?: ContainerSlot.empty(id)
    }

    private fun itemId(stack: ItemStack): String {
        return Item.itemRegistry.getNameForObject(stack.item)?.toString()
            ?: "unknown:${Item.getIdFromItem(stack.item)}"
    }

    private fun blockId(block: Block): String {
        return Block.blockRegistry.getNameForObject(block)?.toString()
            ?: "unknown:${Block.getIdFromBlock(block)}"
    }

    private fun floorBlock(value: Double): Int = floor(value).toInt()

    private fun ItemStack.canMerge(other: ItemStack): Boolean {
        return item == other.item &&
            itemDamage == other.itemDamage &&
            ItemStack.areItemStackTagsEqual(this, other)
    }

    private data class LegacyContainer(
        val key: String,
        val slots: Array<ItemStack?>,
        val stackLimit: Int,
        val canInsert: (Int, ItemStack) -> Boolean,
        val markDirty: (Array<ItemStack?>) -> Unit,
    ) {
        fun validSlot(slotId: Int) = slotId in slots.indices

        fun slot(slotId: Int): ItemStack? = slots.getOrNull(slotId)

        fun setSlot(slotId: Int, stack: ItemStack?) {
            slots[slotId] = stack
        }

        fun slotIds(): IntRange = slots.indices

        fun markDirty() {
            markDirty(slots)
        }
    }

    private data class InsertStep(
        val slotId: Int,
        val count: Int,
    )

    private data class InsertPlan(
        val count: Int,
        val steps: List<InsertStep>,
    )
}
