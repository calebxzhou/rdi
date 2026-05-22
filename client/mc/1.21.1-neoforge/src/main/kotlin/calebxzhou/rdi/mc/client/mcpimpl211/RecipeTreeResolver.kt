package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.common2.mcp.model.RecipeCost
import calebxzhou.rdi.mc.common2.mcp.model.RecipeIngredient
import calebxzhou.rdi.mc.common2.mcp.model.RecipeMaterialKind
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeResolution
import calebxzhou.rdi.mc.common2.mcp.model.RecipeStack
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeNode
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeP
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeUnresolved

object RecipeTreeResolver {
    fun resolve(query: RecipeTreeQ): RecipeTreeP {
        val goal = RecipeStack(query.outputItemId, query.outputCount.coerceAtLeast(1))
        if (!RecipeProcessIndex.isReady()) {
            return RecipeTreeP(
                goal = goal,
                root = null,
                totalCost = listOf(RecipeCost(RecipeMaterialKind.ITEM, goal.itemId, goal.count)),
                unresolved = listOf(RecipeUnresolved(goal, "jei_not_ready")),
            )
        }
        val context = Context(query)
        val root = context.resolve(goal, 0, emptySet())
        return RecipeTreeP(
            goal = goal,
            root = root,
            totalCost = context.costs.recipeCosts(),
            leftovers = context.leftovers.recipeCosts(),
            unresolved = context.unresolved,
        )
    }

    private class Context(
        private val query: RecipeTreeQ,
    ) {
        val costs = linkedMapOf<CostKey, Int>()
        val leftovers = linkedMapOf<CostKey, Int>()
        val unresolved = mutableListOf<RecipeUnresolved>()

        fun resolve(stack: RecipeStack, depth: Int, path: Set<String>): RecipeTreeNode {
            if (stack.itemId in path) {
                costs.addItem(stack.itemId, stack.count)
                return unresolvedNode(stack, "cycle")
            }
            val process = processFor(stack)
            if (process == null) {
                costs.addItem(stack.itemId, stack.count)
                return RecipeTreeNode(stack = stack)
            }
            if (depth >= query.maxDepth) {
                costs.addItem(stack.itemId, stack.count)
                return unresolvedNode(stack, "max_depth")
            }

            val outputPerBatch = process.outputs.firstOrNull { it.itemId == stack.itemId }?.count?.coerceAtLeast(1) ?: 1
            val batches = stack.count.ceilDiv(outputPerBatch)
            val inputAmounts = process.inputAmounts()
            if (inputAmounts.isEmpty()) {
                costs.addItem(stack.itemId, stack.count)
                return unresolvedNode(stack, process, "recipe_has_no_inputs")
            }
            val children = mutableListOf<RecipeTreeNode>()
            for ((ingredient, amount) in inputAmounts) {
                val required = amount * batches
                ingredient.fluids.forEach { costs.addFluid(it.fluidId, it.amount * required) }
                val item = ingredient.itemChoice(stack.itemId)
                if (item == null) {
                    if (ingredient.fluids.isEmpty()) {
                        unresolved += RecipeUnresolved(RecipeStack(stack.itemId, required), "ingredient_no_item_choice")
                    }
                    continue
                }
                item.remainder?.let { leftovers.addItem(it.itemId, it.count * item.count * required) }
                children += resolve(
                    stack = item.copy(count = item.count * required),
                    depth = depth + 1,
                    path = path + stack.itemId,
                )
            }
            val leftoverCount = batches * outputPerBatch - stack.count
            if (leftoverCount > 0) leftovers.addItem(stack.itemId, leftoverCount)
            return RecipeTreeNode(
                stack = stack,
                process = process,
                batches = batches,
                outputPerBatch = outputPerBatch,
                children = children,
            )
        }

        private fun processFor(stack: RecipeStack): RecipeProcess? {
            val candidates = RecipeProcessIndex.recipesByOutputItem(stack.itemId)
            if (candidates.isEmpty()) return null
            val resolution = query.resolutions.firstOrNull { it.outputItemId == stack.itemId }
            val resolved = resolution?.let { candidates.matching(it) }
            if (resolution != null && resolved == null) {
                unresolved += RecipeUnresolved(stack, "recipe_resolution_not_found")
            }
            return resolved ?: candidates.first()
        }

        private fun unresolvedNode(stack: RecipeStack, reason: String): RecipeTreeNode {
            unresolved += RecipeUnresolved(stack, reason)
            return RecipeTreeNode(stack = stack, unresolvedReason = reason)
        }

        private fun unresolvedNode(stack: RecipeStack, process: RecipeProcess, reason: String): RecipeTreeNode {
            unresolved += RecipeUnresolved(stack, reason)
            return RecipeTreeNode(stack = stack, process = process, unresolvedReason = reason)
        }

        private fun RecipeIngredient.itemChoice(outputItemId: String): RecipeStack? {
            val resolution = query.resolutions.firstOrNull { it.outputItemId == outputItemId }
            val resolvedItem = resolution?.ingredientItemId?.let { itemId ->
                items.firstOrNull { it.itemId == itemId }
                    ?: tags.asSequence().flatMap { it.examples.asSequence() }.firstOrNull { it.itemId == itemId }
            }
            return resolvedItem ?: items.firstOrNull() ?: tags.firstNotNullOfOrNull { it.examples.firstOrNull() }
        }
    }

    private fun List<RecipeProcess>.matching(resolution: RecipeResolution): RecipeProcess? {
        return firstOrNull { process ->
            resolution.recipeId == null || process.recipeId == resolution.recipeId
        }
    }

    private fun RecipeProcess.inputAmounts(): List<Pair<RecipeIngredient, Int>> {
        val recipeShape = shape ?: return inputs.map { it to it.count }
        val counts = linkedMapOf<String, Int>()
        recipeShape.pattern.forEach { row ->
            row.forEach { symbol ->
                if (symbol != ' ') {
                    val key = symbol.toString()
                    counts[key] = counts.getOrDefault(key, 0) + 1
                }
            }
        }
        return counts.mapNotNull { (symbol, count) ->
            recipeShape.key[symbol]?.let { it to it.count * count }
        }
    }

    private fun Int.ceilDiv(divisor: Int): Int {
        return (this + divisor - 1) / divisor
    }

    private fun MutableMap<CostKey, Int>.addItem(itemId: String, amount: Int) {
        val key = CostKey(RecipeMaterialKind.ITEM, itemId)
        this[key] = getOrDefault(key, 0) + amount
    }

    private fun MutableMap<CostKey, Int>.addFluid(fluidId: String, amount: Int) {
        val key = CostKey(RecipeMaterialKind.FLUID, fluidId)
        this[key] = getOrDefault(key, 0) + amount
    }

    private fun Map<CostKey, Int>.recipeCosts(): List<RecipeCost> {
        return map { (key, amount) -> RecipeCost(key.kind, key.id, amount) }
    }

    private data class CostKey(
        val kind: RecipeMaterialKind,
        val id: String,
    )
}
