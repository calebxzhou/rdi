package calebxzhou.rdi.mc.server.mcp

import calebxzhou.rdi.mc.common2.mcp.McpBadSlotError
import calebxzhou.rdi.mc.common2.mcp.McpBlockError
import calebxzhou.rdi.mc.common2.mcp.model.ActionResult
import calebxzhou.rdi.mc.common2.mcp.model.BlockActionP
import calebxzhou.rdi.mc.common2.mcp.model.BlockActionForm
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockBreakDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockHarvestResultP
import calebxzhou.rdi.mc.common2.mcp.model.BlockHarvestResultQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockPlaceDiscreteQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockUseItemP
import calebxzhou.rdi.mc.common2.mcp.model.BlockUseItemQ
import calebxzhou.rdi.mc.common2.mcp.model.RBlockAABB
import calebxzhou.rdi.mc.common2.mcp.model.RBlockPos
import net.minecraft.block.Block
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.AxisAlignedBB
import net.minecraftforge.common.ForgeHooks

private typealias Ok = ActionResult.Ok<Unit>
private typealias Err = ActionResult.Err

object BlockMcpHandler1710 {
    private const val BLOCK_BATCH_LIMIT = 2048
    private val HOTBAR_SLOTS = 0..8

    fun breakBox(req: BlockBreakBoxQ, player: EntityPlayerMP): Result<BlockActionP> = runCatching {
        breakPositions(player, processBox(req.startPos, req.deltaPos, req.form), req.toolInvSlot, req.noPickup, req.test)
    }

    fun breakDiscrete(req: BlockBreakDiscreteQ, player: EntityPlayerMP): Result<BlockActionP> = runCatching {
        if (req.poses.size > BLOCK_BATCH_LIMIT) {
            throw McpBlockError("${req.poses.size} exceeds limit $BLOCK_BATCH_LIMIT")
        }
        breakPositions(player, req.poses, req.toolInvSlot, req.noPickup, req.test)
    }

    fun placeBox(req: BlockPlaceBoxQ, player: EntityPlayerMP): Result<BlockActionP> = runCatching {
        val block = resolveBlock(req.blockId)
        if (countPlaceItems(player, block) == 0) {
            throw McpBlockError("insufficient item")
        }
        place(player, processBox(req.startPos, req.deltaPos, req.form), block, req.state, req.test)
    }

    fun placeDiscrete(req: BlockPlaceDiscreteQ, player: EntityPlayerMP): Result<BlockActionP> = runCatching {
        if (req.targets.size > BLOCK_BATCH_LIMIT) {
            throw McpBlockError("${req.targets.size} exceeds limit $BLOCK_BATCH_LIMIT")
        }
        val block = resolveBlock(req.blockId)
        if (countPlaceItems(player, block) == 0) {
            throw McpBlockError("insufficient item")
        }
        val failures = linkedMapOf<Err, MutableList<RBlockPos>>()
        var count = 0
        for (target in req.targets) {
            when (val result = runCatching { placeOne(player, target.pos, block, target.state, req.test) }.getOrElse(::failureResult)) {
                is ActionResult.Ok<*> -> count++
                is Err -> failures.getOrPut(result) { mutableListOf() } += target.pos
            }
        }
        BlockActionP(count, failures, req.test)
    }

    fun harvestResult(req: BlockHarvestResultQ, player: EntityPlayerMP): Result<BlockHarvestResultP> = runCatching {
        val world = player.worldObj
        val pos = req.pos
        if (!world.blockExists(pos.x, pos.y, pos.z)) {
            throw McpBlockError("block position is not loaded")
        }
        val block = world.getBlock(pos.x, pos.y, pos.z)
        if (block == null || block == Blocks.air) {
            throw McpBlockError("block is air")
        }
        val meta = world.getBlockMetadata(pos.x, pos.y, pos.z)
        val tool = req.invSlot?.let { slot ->
            if (slot !in player.inventory.mainInventory.indices) {
                throw McpBadSlotError()
            }
            player.inventory.mainInventory[slot]
        } ?: player.inventory.getCurrentItem()
        val harvestable = canHarvest(player, block, meta, req.invSlot, tool)
        val drops = if (harvestable) {
            val fortune = tool?.let { EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, it) } ?: 0
            block.getDrops(world, pos.x, pos.y, pos.z, meta, fortune).harvestStacks()
        } else {
            emptyList()
        }
        BlockHarvestResultP(
            pos = pos,
            blockId = blockId(block),
            tool = tool.harvestStack(),
            toolSlot = req.invSlot,
            harvestable = harvestable,
            drops = drops,
        )
    }

    private fun processBox(startPos: RBlockPos, deltaPos: RBlockPos, form: BlockActionForm): List<RBlockPos> {
        val box = RBlockAABB.fromDelta(startPos, deltaPos)
        val positions = when (form) {
            BlockActionForm.BOX -> box.posListBox
            BlockActionForm.RING -> box.posListRing
        }
        if (positions.size > BLOCK_BATCH_LIMIT) {
            throw McpBlockError("${positions.size} exceeds limit $BLOCK_BATCH_LIMIT")
        }
        return positions
    }

    private fun breakPositions(
        player: EntityPlayerMP,
        positions: List<RBlockPos>,
        toolInvSlot: Int?,
        noPickup: Boolean,
        test: Boolean,
    ): BlockActionP {
        val failures = linkedMapOf<Err, MutableList<RBlockPos>>()
        var count = 0
        for (pos in positions) {
            when (val result = runCatching { breakOne(player, pos, toolInvSlot, noPickup, test) }.getOrElse(::failureResult)) {
                is ActionResult.Ok<*> -> count++
                is Err -> failures.getOrPut(result) { mutableListOf() } += pos
            }
        }
        return BlockActionP(count, failures, test)
    }

    private fun breakOne(
        player: EntityPlayerMP,
        pos: RBlockPos,
        toolInvSlot: Int?,
        noPickup: Boolean,
        test: Boolean,
    ): ActionResult<Unit> {
        checkBreakTarget(player, pos)?.let { return it }
        if (toolInvSlot != null && (toolInvSlot !in HOTBAR_SLOTS || toolInvSlot !in player.inventory.mainInventory.indices)) {
            return Err("bad slot")
        }
        if (toolInvSlot != null && player.inventory.mainInventory[toolInvSlot] == null) {
            return Err("bad slot")
        }
        if (test) {
            return Ok(Unit)
        }
        val beforeDropIds = if (noPickup) emptySet() else freshItemEntityIds(player, pos)
        val broken = withHotbarSlot(player, toolInvSlot) {
            player.theItemInWorldManager.tryHarvestBlock(pos.x, pos.y, pos.z)
        }
        if (!broken) {
            return Err("rejected")
        }
        if (!noPickup) {
            pickupFreshBlockDrops(player, pos, beforeDropIds)
        }
        player.inventory.markDirty()
        player.openContainer.detectAndSendChanges()
        return Ok(Unit)
    }

    private fun place(
        player: EntityPlayerMP,
        positions: List<RBlockPos>,
        block: Block,
        state: Map<String, String>,
        test: Boolean,
    ): BlockActionP {
        val failures = linkedMapOf<Err, MutableList<RBlockPos>>()
        var count = 0
        for (pos in positions) {
            when (val result = runCatching { placeOne(player, pos, block, state, test) }.getOrElse(::failureResult)) {
                is ActionResult.Ok<*> -> count++
                is Err -> failures.getOrPut(result) { mutableListOf() } += pos
            }
        }
        return BlockActionP(count, failures, test)
    }

    private fun placeOne(
        player: EntityPlayerMP,
        pos: RBlockPos,
        block: Block,
        state: Map<String, String>,
        test: Boolean,
    ): ActionResult<Unit> {
        checkPlaceTarget(player, pos)?.let { return it }
        if (player.worldObj.getBlock(pos.x, pos.y, pos.z) != Blocks.air) {
            return Err("not air")
        }
        if (unsupportedState(state)) {
            return Err("unsupported state")
        }
        val meta = stateMeta(state)
        val slot = findPlaceSlot(player, block) ?: return Err("insufficient item")
        if (test) {
            return Ok(Unit)
        }
        val stack = player.inventory.mainInventory[slot] ?: return Err("insufficient item")
        val placed = withHotbarSlot(player, slot) {
            stack.tryPlaceItemIntoWorld(
                player,
                player.worldObj,
                pos.x,
                pos.y - 1,
                pos.z,
                1,
                0.5f,
                1.0f,
                0.5f,
            )
        }
        if (!placed || player.worldObj.getBlock(pos.x, pos.y, pos.z) == Blocks.air) {
            return Err("rejected")
        }
        if (meta != null && player.worldObj.getBlock(pos.x, pos.y, pos.z) == block) {
            player.worldObj.setBlockMetadataWithNotify(pos.x, pos.y, pos.z, meta, 3)
        }
        player.inventory.markDirty()
        player.openContainer.detectAndSendChanges()
        return Ok(Unit)
    }

    private fun checkPlaceTarget(player: EntityPlayerMP, pos: RBlockPos): Err? {
        val world = player.worldObj
        if (!world.blockExists(pos.x, pos.y, pos.z)) {
            return Err("chunk not loaded")
        }
        if (!world.blockExists(pos.x, pos.y - 1, pos.z)) {
            return Err("base chunk not loaded")
        }
        return null
    }

    private fun resolveBlock(id: String): Block {
        val normalized = normalizeBlockId(id)
        val legacy = normalized.removePrefix("minecraft:")
        val block = Block.blockRegistry.getObject(normalized) as? Block
            ?: Block.blockRegistry.getObject(id) as? Block
            ?: Block.blockRegistry.getObject(legacy) as? Block
            ?: throw McpBlockError("block id $id not found")
        if (block == Blocks.air) {
            throw McpBlockError("block is air")
        }
        return block
    }

    private fun normalizeBlockId(id: String): String {
        val trimmed = id.trim()
        return if (":" in trimmed) trimmed else "minecraft:$trimmed"
    }

    private fun countPlaceItems(player: EntityPlayerMP, block: Block): Int {
        var count = 0
        for (slot in HOTBAR_SLOTS) {
            val stack = player.inventory.mainInventory.getOrNull(slot)
            if (stack != null && stack.stackSize > 0 && (stack.item as? ItemBlock)?.field_150939_a == block) {
                count += stack.stackSize
            }
        }
        return count
    }

    private fun findPlaceSlot(player: EntityPlayerMP, block: Block): Int? {
        return HOTBAR_SLOTS.firstOrNull { slot ->
            val stack = player.inventory.mainInventory.getOrNull(slot)
            stack != null && stack.stackSize > 0 && (stack.item as? ItemBlock)?.field_150939_a == block
        }
    }

    private fun unsupportedState(state: Map<String, String>): Boolean {
        return state.keys.any { it != "meta" && it != "metadata" } || stateMeta(state) == null && state.isNotEmpty()
    }

    private fun stateMeta(state: Map<String, String>): Int? {
        val text = state["meta"] ?: state["metadata"] ?: return null
        return text.toIntOrNull()?.takeIf { it in 0..15 }
    }

    private fun checkBreakTarget(player: EntityPlayerMP, pos: RBlockPos): Err? {
        val world = player.worldObj
        if (!world.blockExists(pos.x, pos.y, pos.z)) {
            return Err("chunk not loaded")
        }
        if (world.getBlock(pos.x, pos.y, pos.z) == Blocks.air) {
            return Err("air")
        }
        return null
    }

    private fun freshItemEntityIds(player: EntityPlayerMP, pos: RBlockPos): Set<Int> {
        return itemEntitiesAround(player, pos).mapTo(mutableSetOf()) { it.entityId }
    }

    private fun pickupFreshBlockDrops(player: EntityPlayerMP, pos: RBlockPos, beforeIds: Set<Int>) {
        for (entity in itemEntitiesAround(player, pos)) {
            if (entity.entityId in beforeIds || entity.isDead) {
                continue
            }
            val stack = entity.entityItem ?: continue
            if (stack.stackSize <= 0) {
                entity.setDead()
                continue
            }
            player.inventory.addItemStackToInventory(stack)
            if (stack.stackSize <= 0) {
                entity.setDead()
            } else {
                entity.setEntityItemStack(stack)
            }
        }
    }

    private fun itemEntitiesAround(player: EntityPlayerMP, pos: RBlockPos): List<EntityItem> {
        val box = AxisAlignedBB.getBoundingBox(
            pos.x - 1.0,
            pos.y - 1.0,
            pos.z - 1.0,
            pos.x + 2.0,
            pos.y + 2.0,
            pos.z + 2.0,
        )
        return player.worldObj.getEntitiesWithinAABB(EntityItem::class.java, box).filterIsInstance<EntityItem>()
    }

    fun useItemOn(req: BlockUseItemQ, player: EntityPlayerMP): Result<BlockUseItemP> = runCatching {
        val world = player.worldObj
        val pos = req.pos
        if (!world.blockExists(pos.x, pos.y, pos.z)) {
            throw McpBlockError("block position is not loaded")
        }
        val side = faceSide(req.face)
        if (req.hitX !in 0.0..1.0 || req.hitY !in 0.0..1.0 || req.hitZ !in 0.0..1.0) {
            throw McpBlockError("hit point must be 0.0..1.0")
        }
        if (req.invSlot !in HOTBAR_SLOTS || req.invSlot !in player.inventory.mainInventory.indices) {
            throw McpBadSlotError()
        }
        val itemStack = player.inventory.mainInventory[req.invSlot]
            ?: throw McpBlockError("slot ${req.invSlot} is empty")
        if (itemStack.stackSize <= 0) {
            throw McpBlockError("slot ${req.invSlot} is empty")
        }
        val blockBefore = blockId(world.getBlock(pos.x, pos.y, pos.z))
        if (world.getBlock(pos.x, pos.y, pos.z) == Blocks.air) {
            throw McpBlockError("block is air")
        }
        val itemBefore = stackText(itemStack)
        val oldSlot = player.inventory.currentItem
        val result = try {
            player.inventory.currentItem = req.invSlot
            player.theItemInWorldManager.activateBlockOrUseItem(
                player,
                world,
                itemStack,
                pos.x,
                pos.y,
                pos.z,
                side,
                req.hitX.toFloat(),
                req.hitY.toFloat(),
                req.hitZ.toFloat(),
            )
        } finally {
            player.inventory.currentItem = oldSlot
        }
        player.inventory.markDirty()
        BlockUseItemP(
            pos = pos,
            invSlot = req.invSlot,
            itemBefore = itemBefore,
            itemAfter = stackText(player.inventory.mainInventory[req.invSlot]),
            blockBefore = blockBefore,
            blockAfter = blockId(world.getBlock(pos.x, pos.y, pos.z)),
            result = if (result) "success" else "pass",
        )
    }

    private fun canHarvest(
        player: EntityPlayerMP,
        block: Block,
        meta: Int,
        invSlot: Int?,
        tool: ItemStack?,
    ): Boolean {
        if (invSlot == null || invSlot in HOTBAR_SLOTS) {
            val oldSlot = player.inventory.currentItem
            if (invSlot != null) {
                player.inventory.currentItem = invSlot
            }
            return try {
                block.canHarvestBlock(player, meta)
            } finally {
                player.inventory.currentItem = oldSlot
            }
        }
        if (block.material.isToolNotRequired) {
            return true
        }
        return tool?.let { ForgeHooks.canToolHarvestBlock(block, meta, it) } ?: false
    }

    private inline fun <T> withHotbarSlot(player: EntityPlayerMP, slot: Int?, action: () -> T): T {
        val oldSlot = player.inventory.currentItem
        if (slot != null) {
            player.inventory.currentItem = slot
        }
        return try {
            action()
        } finally {
            player.inventory.currentItem = oldSlot
        }
    }

    private fun failureResult(e: Throwable): Err {
        return if (e is McpBlockError) {
            Err(e.detail)
        } else {
            Err(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun List<ItemStack>.harvestStacks(): List<BlockHarvestResultP.HarvestStack> {
        return asSequence()
            .filter { it.stackSize > 0 }
            .groupingBy(::itemId)
            .fold(0) { count, stack -> count + stack.stackSize }
            .map { (itemId, count) -> BlockHarvestResultP.HarvestStack(itemId, count) }
    }

    private fun ItemStack?.harvestStack(): BlockHarvestResultP.HarvestStack {
        return if (this == null || stackSize <= 0) {
            BlockHarvestResultP.HarvestStack("air", 0)
        } else {
            BlockHarvestResultP.HarvestStack(itemId(this), stackSize)
        }
    }

    private fun stackText(stack: ItemStack?): String {
        return if (stack == null || stack.stackSize <= 0) {
            "0x air"
        } else {
            "${stack.stackSize}x ${itemId(stack)}"
        }
    }

    private fun faceSide(face: String): Int {
        return when (face.lowercase()) {
            "down" -> 0
            "up" -> 1
            "north" -> 2
            "south" -> 3
            "west" -> 4
            "east" -> 5
            else -> throw McpBlockError("invalid face $face")
        }
    }

    private fun itemId(stack: ItemStack): String {
        return Item.itemRegistry.getNameForObject(stack.item)?.toString()
            ?: "unknown:${Item.getIdFromItem(stack.item)}"
    }

    private fun blockId(block: Block): String {
        return Block.blockRegistry.getNameForObject(block)?.toString()
            ?: "unknown:${Block.getIdFromBlock(block)}"
    }
}
