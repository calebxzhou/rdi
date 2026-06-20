package calebxzhou.rdi.mc.server.mcp

import calebxzhou.rdi.mc.common2.mcp.McpCraftError
import calebxzhou.rdi.mc.common2.mcp.model.CraftP
import calebxzhou.rdi.mc.common2.mcp.model.CraftQ
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.inventory.InventoryCrafting
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.item.crafting.IRecipe

object CraftMcpHandler1710 {
    private const val INVENTORY_SIZE = 36

    fun craft(req: CraftQ, player: EntityPlayerMP): Result<CraftP> = runCatching {
        if (req.times !in 1..64) {
            throw McpCraftError("times must be 1..64, got ${req.times}")
        }
        val shape = req.shape()
        val plan = craftPlan(shape, req.times, player).getOrThrow()
        if (!req.test) {
            val inventory = player.inventory.mainInventory
            val updatedInventory = inventory.map { it?.copy() }.toMutableList()
            plan.consumed.forEach { (slotId, count) ->
                val stack = updatedInventory[slotId]
                    ?: throw McpCraftError("inventory changed before applying craft; slot $slotId is empty")
                if (stack.stackSize < count) {
                    throw McpCraftError("inventory changed before applying craft; slot $slotId only has ${stack.stackSize}")
                }
                stack.stackSize -= count
                if (stack.stackSize <= 0) {
                    updatedInventory[slotId] = null
                }
            }
            plan.insertions.forEach { stack ->
                if (!insertStack(updatedInventory, stack.copy())) {
                    throw McpCraftError("inventory changed before applying craft result; cannot insert ${stackText(stack)}")
                }
            }
            updatedInventory.forEachIndexed { slotId, stack ->
                inventory[slotId] = stack
            }
            player.inventory.markDirty()
            player.openContainer.detectAndSendChanges()
        }
        CraftP(
            result = stackText(plan.result),
            consumed = plan.consumed,
            recipeId = plan.recipeId,
            test = req.test,
        )
    }

    private fun craftPlan(shape: CraftShape, times: Int, player: EntityPlayerMP): Result<CraftPlan> = runCatching {
        val simulatedInventory = player.inventory.mainInventory.map { it?.copy() }.toMutableList()
        val consumed = linkedMapOf<Int, Int>()
        val insertions = mutableListOf<ItemStack>()
        var recipeId: String? = null
        var crafted: ItemStack? = null

        repeat(times) { index ->
            runCatching {
                val input = shape.input(simulatedInventory)
                val recipe = matchingRecipe(input.grid, player)
                val currentRecipeId = recipe?.javaClass?.name ?: "minecraft:repair"
                if (recipeId != null && recipeId != currentRecipeId) {
                    throw McpCraftError("recipe changed across repeated crafts: first=$recipeId current=$currentRecipeId")
                }
                recipeId = currentRecipeId
                val result = CraftingManager.getInstance().findMatchingRecipe(input.grid, player.worldObj)
                    ?: throw McpCraftError("no matching crafting recipe for pattern input")
                if (result.stackSize <= 0) {
                    throw McpCraftError("recipe $currentRecipeId assembled empty result")
                }
                val remainingItems = input.gridStacks.mapNotNull(::containerItem)

                input.consumed.forEach { (slotId, count) ->
                    simulatedInventory[slotId]?.let { stack ->
                        stack.stackSize -= count
                        if (stack.stackSize <= 0) {
                            simulatedInventory[slotId] = null
                        }
                    }
                }
                if (!insertStack(simulatedInventory, result.copy())) {
                    throw McpCraftError("not enough inventory space for craft result ${stackText(result)}")
                }
                remainingItems.forEach { remaining ->
                    if (!insertStack(simulatedInventory, remaining.copy())) {
                        throw McpCraftError("not enough inventory space for remaining item ${stackText(remaining)}")
                    }
                }

                input.consumed.forEach { (slotId, count) ->
                    consumed[slotId] = consumed.getOrDefault(slotId, 0) + count
                }
                crafted = crafted?.also { it.stackSize += result.stackSize } ?: result.copy()
                insertions += result.copy()
                insertions += remainingItems
            }.getOrElse {
                if (it is McpCraftError) {
                    throw McpCraftError("Craft Time $index,${it.detail}")
                }
                throw it
            }
        }

        CraftPlan(
            result = crafted ?: throw McpCraftError("craft produced no recipe after $times times"),
            consumed = consumed,
            recipeId = recipeId ?: throw McpCraftError("craft produced no recipe after $times times"),
            insertions = insertions,
        )
    }

    private fun CraftQ.shape(): CraftShape {
        val rows = pattern.split('|')
        if (rows.isEmpty()) {
            throw McpCraftError("pattern is empty")
        }
        if (rows.size > 3) {
            throw McpCraftError("pattern has ${rows.size} rows, max is 3")
        }
        rows.forEachIndexed { index, row ->
            if (row.isEmpty()) {
                throw McpCraftError("pattern row $index is empty")
            }
            if (row.length > 3) {
                throw McpCraftError("pattern row $index has ${row.length} columns, max is 3")
            }
        }
        val symbolSlots = key.mapKeys { (symbol, _) ->
            if (symbol.length != 1 || symbol[0].isWhitespace()) {
                throw McpCraftError("key symbol must be one non-space character, got '$symbol'")
            }
            symbol[0]
        }
        val gridSlots = MutableList<Int?>(9) { null }
        var ingredientCount = 0
        rows.forEachIndexed { row, text ->
            text.forEachIndexed { column, symbol ->
                if (symbol.isWhitespace()) return@forEachIndexed
                val slotId = symbolSlots[symbol] ?: throw McpCraftError("pattern symbol '$symbol' has no key slot")
                if (slotId !in 0 until INVENTORY_SIZE) {
                    throw McpCraftError("slot $slotId for symbol '$symbol' is outside player inventory 0..35")
                }
                gridSlots[column + row * 3] = slotId
                ingredientCount++
            }
        }
        if (ingredientCount == 0) {
            throw McpCraftError("pattern has no ingredient symbols")
        }
        return CraftShape(gridSlots)
    }

    private fun CraftShape.input(inventory: List<ItemStack?>): BatchInput {
        val grid = InventoryCrafting(DummyCraftContainer, 3, 3)
        val gridStacks = MutableList<ItemStack?>(9) { null }
        val consumed = linkedMapOf<Int, Int>()
        gridSlots.forEachIndexed { index, slotId ->
            if (slotId == null) return@forEachIndexed
            val stack = inventory[slotId]
            val count = consumed.getOrDefault(slotId, 0)
            if (stack == null || stack.stackSize <= count) {
                throw McpCraftError("slot $slotId insufficient items for pattern cell $index")
            }
            consumed[slotId] = count + 1
            val cellStack = stack.copy().also { it.stackSize = 1 }
            grid.setInventorySlotContents(index, cellStack)
            gridStacks[index] = cellStack
        }
        return BatchInput(grid, gridStacks, consumed)
    }

    private fun matchingRecipe(grid: InventoryCrafting, player: EntityPlayerMP): IRecipe? {
        return CraftingManager.getInstance().recipeList
            .filterIsInstance<IRecipe>()
            .firstOrNull { it.matches(grid, player.worldObj) }
    }

    private fun containerItem(stack: ItemStack?): ItemStack? {
        if (stack == null || !stack.item.hasContainerItem(stack)) {
            return null
        }
        val remaining = stack.item.getContainerItem(stack) ?: return null
        return remaining.takeIf { it.stackSize > 0 }
    }

    private fun insertStack(inventory: MutableList<ItemStack?>, stack: ItemStack): Boolean {
        if (stack.stackSize <= 0) return true
        for (slotId in inventory.indices) {
            val target = inventory[slotId]
            if (target != null && target.stackSize > 0 && target.canMerge(stack)) {
                val moved = minOf(stack.stackSize, target.maxStackSize - target.stackSize)
                if (moved > 0) {
                    target.stackSize += moved
                    stack.stackSize -= moved
                    if (stack.stackSize <= 0) return true
                }
            }
        }
        for (slotId in inventory.indices) {
            val target = inventory[slotId]
            if (target == null || target.stackSize <= 0) {
                val moved = minOf(stack.stackSize, stack.maxStackSize)
                inventory[slotId] = stack.copy().also { it.stackSize = moved }
                stack.stackSize -= moved
                if (stack.stackSize <= 0) return true
            }
        }
        return stack.stackSize <= 0
    }

    private fun ItemStack.canMerge(other: ItemStack): Boolean {
        return isItemEqual(other) && ItemStack.areItemStackTagsEqual(this, other)
    }

    private fun stackText(stack: ItemStack): String {
        return "${stack.stackSize}x ${itemId(stack)}"
    }

    private fun itemId(stack: ItemStack): String {
        return Item.itemRegistry.getNameForObject(stack.item)?.toString()
            ?: "unknown:${Item.getIdFromItem(stack.item)}"
    }

    private object DummyCraftContainer : Container() {
        override fun canInteractWith(player: EntityPlayer): Boolean = true
    }

    private data class CraftShape(
        val gridSlots: List<Int?>,
    )

    private data class BatchInput(
        val grid: InventoryCrafting,
        val gridStacks: List<ItemStack?>,
        val consumed: Map<Int, Int>,
    )

    private data class CraftPlan(
        val result: ItemStack,
        val consumed: Map<Int, Int>,
        val recipeId: String,
        val insertions: List<ItemStack>,
    )
}
