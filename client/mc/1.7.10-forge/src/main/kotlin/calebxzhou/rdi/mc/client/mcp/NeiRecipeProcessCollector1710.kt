package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.model.RecipeFluidStack
import calebxzhou.rdi.mc.common2.mcp.model.RecipeIngredient
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeShape
import calebxzhou.rdi.mc.common2.mcp.model.RecipeStack
import codechicken.nei.PositionedStack
import codechicken.nei.recipe.GuiCraftingRecipe
import codechicken.nei.recipe.GuiRecipeTab
import codechicken.nei.recipe.IRecipeHandler
import codechicken.nei.recipe.ShapedRecipeHandler
import codechicken.nei.recipe.ShapelessRecipeHandler
import codechicken.nei.recipe.StackInfo
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import java.util.zip.CRC32

object NeiRecipeProcessCollector1710 {
    private const val CRAFTING_SLOT_X = 25
    private const val CRAFTING_SLOT_Y = 6
    private const val CRAFTING_SLOT_STEP = 18
    private val SHAPE_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun isReady(): Boolean {
        return GuiCraftingRecipe.craftinghandlers.isNotEmpty() || GuiCraftingRecipe.serialCraftingHandlers.isNotEmpty()
    }

    fun collect(outputItemId: String): List<RecipeProcess> {
        val output = itemStack(outputItemId)?.let(StackInfo::normalizeRecipeQueryStack) ?: return emptyList()
        return GuiCraftingRecipe.getCraftingHandlers("item", output)
            .flatMap { handler -> handler.recipeProcesses() }
            .distinctBy { it.recipeId }
    }

    private fun IRecipeHandler.recipeProcesses(): List<RecipeProcess> {
        val handlerId = getHandlerId()
        val type = handlerType()
        return (0 until numRecipes()).mapNotNull { recipeIndex ->
            runCatching {
                val ingredientStacks = getIngredientStacks(recipeIndex)
                val inputs = ingredientStacks.mapNotNull { it.recipeIngredient() }
                val resultStack = getResultStack(recipeIndex)
                val otherStacks = getOtherStacks(recipeIndex).mapNotNull { it.recipeIngredient() }
                val outputs = resultStack?.recipeOutputs().orEmpty()
                val fluidOutputs = resultStack?.recipeFluidOutputs().orEmpty()
                val otherAsOutputs = resultStack == null
                val process = RecipeProcess(
                    recipeId = recipeId(handlerId, recipeIndex, inputs, outputs, fluidOutputs, otherStacks),
                    type = type,
                    outputs = outputs,
                    fluidOutputs = fluidOutputs,
                    inputs = inputs,
                    catalysts = if (otherAsOutputs) emptyList() else otherStacks,
                    renderOnly = emptyList(),
                    shape = if (this is ShapedRecipeHandler && this !is ShapelessRecipeHandler) {
                        ingredientStacks.recipeShape()
                    } else {
                        null
                    },
                    special = false,
                )
                if (otherAsOutputs) {
                    process.copy(
                        outputs = otherStacks.flatMap { it.items },
                        fluidOutputs = otherStacks.flatMap { it.fluids },
                    )
                } else {
                    process
                }
            }.getOrNull()
        }
    }

    private fun IRecipeHandler.handlerType(): String {
        if (this is ShapelessRecipeHandler) return "minecraft:crafting_shapeless"
        if (this is ShapedRecipeHandler) return "minecraft:crafting_shaped"
        val info = GuiRecipeTab.getHandlerInfo(this)
        val name = info?.getHandlerName()?.takeIf { it.isNotBlank() } ?: getHandlerId()
        return "nei:${name.replace(' ', '_')}"
    }

    private fun List<PositionedStack>.recipeShape(): RecipeShape? {
        val slots = mapNotNull { stack ->
            val ingredient = stack.recipeIngredient() ?: return@mapNotNull null
            val xOffset = stack.relx - CRAFTING_SLOT_X
            val yOffset = stack.rely - CRAFTING_SLOT_Y
            if (xOffset < 0 || yOffset < 0 ||
                xOffset % CRAFTING_SLOT_STEP != 0 || yOffset % CRAFTING_SLOT_STEP != 0
            ) {
                return null
            }
            ShapeSlot(xOffset / CRAFTING_SLOT_STEP, yOffset / CRAFTING_SLOT_STEP, ingredient)
        }
        if (slots.isEmpty()) return null

        val minX = slots.minOf { it.x }
        val maxX = slots.maxOf { it.x }
        val minY = slots.minOf { it.y }
        val maxY = slots.maxOf { it.y }
        if (maxX - minX >= 3 || maxY - minY >= 3) return null

        val ingredientSymbols = linkedMapOf<RecipeIngredient, Char>()
        slots.forEach { slot ->
            ingredientSymbols.getOrPut(slot.ingredient) {
                SHAPE_SYMBOLS.getOrNull(ingredientSymbols.size) ?: return null
            }
        }
        val slotsByPosition = slots.associateBy { it.x to it.y }
        val pattern = (minY..maxY).map { y ->
            buildString {
                for (x in minX..maxX) {
                    val ingredient = slotsByPosition[x to y]?.ingredient
                    append(ingredient?.let(ingredientSymbols::get) ?: ' ')
                }
            }
        }
        return RecipeShape(
            pattern = pattern,
            key = ingredientSymbols.entries.associate { (ingredient, symbol) ->
                symbol.toString() to ingredient
            },
        )
    }

    private fun PositionedStack.recipeIngredient(): RecipeIngredient? {
        val itemStacks = items.orEmpty()
        val fluidStacks = itemStacks.mapNotNull { StackInfo.getFluid(it) }
        val normalStacks = itemStacks.filter { StackInfo.getFluid(it) == null }
        if (fluidStacks.isEmpty() && normalStacks.isEmpty()) return null
        return RecipeIngredient(
            items = normalStacks.map(::recipeStack).distinctBy { it.itemId to it.count },
            fluids = fluidStacks.map { RecipeFluidStack(it.getFluid().getName(), it.amount, chanceFloat()) }
                .distinctBy { it.fluidId to it.amount },
            count = item?.stackSize?.takeIf { it > 0 } ?: 1,
            chance = chanceFloat(),
        )
    }

    private fun PositionedStack.recipeOutputs(): List<RecipeStack> {
        return item?.takeIf { StackInfo.getFluid(it) == null }?.let { listOf(recipeStack(it)) }.orEmpty()
    }

    private fun PositionedStack.recipeFluidOutputs(): List<RecipeFluidStack> {
        val fluid = item?.let { StackInfo.getFluid(it) } ?: return emptyList()
        return listOf(RecipeFluidStack(fluid.getFluid().getName(), fluid.amount, chanceFloat()))
    }

    private fun PositionedStack.chanceFloat(): Float {
        return getChance().toFloat() / PositionedStack.CHANCE_FULL.toFloat()
    }

    private fun recipeStack(stack: ItemStack): RecipeStack {
        return RecipeStack(itemId(stack), stack.stackSize.coerceAtLeast(1))
    }

    private fun itemId(stack: ItemStack): String {
        val id = Item.itemRegistry.getNameForObject(stack.item)?.toString()
            ?: return "unknown:${Item.getIdFromItem(stack.item)}"
        val damage = stack.itemDamage
        return if (damage > 0) "$id:$damage" else id
    }

    private fun itemStack(itemId: String): ItemStack? {
        val direct = Item.itemRegistry.getObject(itemId) as? Item
        if (direct != null) return ItemStack(direct, 1, 0)
        val split = itemId.lastIndexOf(':')
        if (split <= 0) return null
        val damage = itemId.substring(split + 1).toIntOrNull() ?: return null
        val id = itemId.substring(0, split)
        val item = Item.itemRegistry.getObject(id) as? Item ?: return null
        return ItemStack(item, 1, damage)
    }

    private fun recipeId(
        handlerId: String,
        recipeIndex: Int,
        inputs: List<RecipeIngredient>,
        outputs: List<RecipeStack>,
        fluidOutputs: List<RecipeFluidStack>,
        otherStacks: List<RecipeIngredient>,
    ): String {
        val content = buildString {
            append(handlerId).append('|').append(recipeIndex).append('|')
            append(inputs.joinToString(";")).append('|')
            append(outputs.joinToString(";")).append('|')
            append(fluidOutputs.joinToString(";")).append('|')
            append(otherStacks.joinToString(";"))
        }
        return "nei:${handlerId.urlPart()}:$recipeIndex:${content.crc32()}"
    }

    private fun String.urlPart(): String {
        return map { ch ->
            if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_'
        }.joinToString("")
    }

    private fun String.crc32(): String {
        val crc = CRC32()
        crc.update(toByteArray())
        return crc.value.toString(16)
    }

    private data class ShapeSlot(
        val x: Int,
        val y: Int,
        val ingredient: RecipeIngredient,
    )
}
