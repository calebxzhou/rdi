package calebxzhou.rdi.mc.server.mcpimpl211.handler

import calebxzhou.rdi.mc.common2.mcp.McpCraftError
import calebxzhou.rdi.mc.common2.mcp.model.CraftP
import calebxzhou.rdi.mc.common2.mcp.model.CraftQ
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeType

object CraftHandler {
    fun craft(req: CraftQ, player: ServerPlayer): Result<CraftP> = runCatching {
        if (req.times !in 1..64) {
            throw McpCraftError("times must be 1..64, got ${req.times}")
        }
        val shape = req.shape()
        val plan = craftPlan(shape, req.times, player).getOrThrow()

        if (!req.test) {
            val inventory = player.inventory.items
            plan.consumed.forEach { (slotId, count) ->
                inventory[slotId].shrink(count)
            }
            plan.insertions.forEach { stack ->
                if (!inventory.insertStack(stack.copy())) {
                    throw McpCraftError("inventory changed before applying craft result; cannot insert $stack")
                }
            }
            player.inventory.setChanged()
            player.inventoryMenu.broadcastChanges()
            player.containerMenu.broadcastChanges()
        }

        CraftP(
            result = plan.result.toString(),
            consumed = plan.consumed,
            recipeId = plan.recipeId.toString(),
            test = req.test,
        )
    }

    private fun craftPlan(shape: CraftShape, times: Int, player: ServerPlayer): Result<CraftPlan> = runCatching {
        val level = player.serverLevel()
        val registryAccess = player.registryAccess()
        val simulatedInventory = player.inventory.items.map(ItemStack::copy).toMutableList()
        val consumed = linkedMapOf<Int, Int>()
        val insertions = mutableListOf<ItemStack>()
        var recipeId: ResourceLocation? = null
        var crafted = ItemStack.EMPTY

        repeat(times) { i ->
            runCatching {
                val input = shape.input(simulatedInventory)
                val holder = level.recipeManager.getRecipeFor(RecipeType.CRAFTING, input.craftingInput, level)
                    .orElse(null) ?: throw McpCraftError("no matching crafting recipe for pattern input")
                if (recipeId != null && recipeId != holder.id()) {
                    throw McpCraftError("recipe changed across repeated crafts: first=$recipeId current=${holder.id()}")
                }
                recipeId = holder.id()
                val recipe = holder.value()
                val result = recipe.assemble(input.craftingInput, registryAccess)
                if (result.isEmpty) {
                    throw McpCraftError("recipe ${holder.id()} assembled empty result")
                }
                val remainingItems =
                    recipe.getRemainingItems(input.craftingInput).filterNot { it.isEmpty }.map(ItemStack::copy)

                input.consumed.forEach { (slotId, count) ->
                    simulatedInventory[slotId].shrink(count)
                }
                if (!simulatedInventory.insertStack(result.copy())) {
                    throw McpCraftError("not enough inventory space for craft result $result")
                }
                remainingItems.forEach { remaining ->
                    if (!simulatedInventory.insertStack(remaining.copy())) {
                        throw McpCraftError("not enough inventory space for remaining item $remaining")
                    }
                }

                input.consumed.forEach { (slotId, count) ->
                    consumed[slotId] = consumed.getOrDefault(slotId, 0) + count
                }
                crafted = if (crafted.isEmpty) {
                    result.copy()
                } else {
                    crafted.apply { grow(result.count) }
                }
                insertions += result.copy()
                insertions += remainingItems
            }.getOrElse {
                if (it is McpCraftError) {
                    throw McpCraftError("Craft Time ${i}," + it.detail)
                }
                throw it
            }
        }

        CraftPlan(
            result = crafted,
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
                if (slotId !in 0..35) {
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

    private fun CraftShape.input(inventory: List<ItemStack>): BatchInput {
        val grid = MutableList(9) { ItemStack.EMPTY }
        val consumed = linkedMapOf<Int, Int>()
        gridSlots.forEachIndexed { index, slotId ->
            if (slotId == null) return@forEachIndexed
            val stack = inventory[slotId]
            val count = consumed.getOrDefault(slotId, 0)
            if (stack.isEmpty || stack.count <= count) {
                throw McpCraftError("slot $slotId ,$stack , insufficient items for pattern cell $index, ${stack.count} now $count needed")
            }
            consumed[slotId] = count + 1
            grid[index] = stack.copyWithCount(1)
        }
        return BatchInput(
            craftingInput = CraftingInput.of(3, 3, grid),
            consumed = consumed,
        )
    }

    private fun MutableList<ItemStack>.insertStack(stack: ItemStack): Boolean {
        if (stack.isEmpty) return true
        for (slotId in indices) {
            val target = this[slotId]
            if (!target.isEmpty && ItemStack.isSameItemSameComponents(target, stack)) {
                val moved = minOf(stack.count, target.maxStackSize - target.count)
                if (moved > 0) {
                    target.grow(moved)
                    stack.shrink(moved)
                    if (stack.isEmpty) return true
                }
            }
        }
        for (slotId in indices) {
            if (this[slotId].isEmpty) {
                val moved = minOf(stack.count, stack.maxStackSize)
                this[slotId] = stack.copyWithCount(moved)
                stack.shrink(moved)
                if (stack.isEmpty) return true
            }
        }
        return stack.isEmpty
    }

    private data class CraftShape(
        val gridSlots: List<Int?>,
    )

    private data class BatchInput(
        val craftingInput: CraftingInput,
        val consumed: Map<Int, Int>,
    )

    private data class CraftPlan(
        val result: ItemStack,
        val consumed: Map<Int, Int>,
        val recipeId: ResourceLocation,
        val insertions: List<ItemStack>,
    )
}
