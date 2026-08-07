package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.common2.mcp.model.RecipeIngredient
import calebxzhou.rdi.mc.common2.mcp.model.RecipeItemTag
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeShape
import calebxzhou.rdi.mc.common2.mcp.model.RecipeStack
import calebxzau.mc.common2021.resId
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.item.crafting.SingleItemRecipe

object VanillaRecipeProcessCollector {
    private const val MAX_DIRECT_ITEMS = 4
    private const val MAX_TAG_EXAMPLES = 3
    private val SHAPE_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun collect(minecraft: Minecraft = Minecraft.getInstance()): List<RecipeProcess> {
        val level = minecraft.level ?: return emptyList()
        val registries = level.registryAccess()
        return level.recipeManager.orderedRecipes.mapNotNull { holder ->
            val recipe = holder.value()
            val output = recipe.getResultItem(registries)
            if (output.isEmpty) return@mapNotNull null
            recipeProcess(holder.id().toString(), recipe, output)
        }
    }

    private fun recipeProcess(recipeId: String, recipe: Recipe<*>, output: ItemStack): RecipeProcess {
        val type = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.serializer).toString()
        val outputStack = recipeStack(output)
        return when (recipe) {
            is ShapedRecipe -> shapedRecipeProcess(recipeId, type, recipe, outputStack)
            is ShapelessRecipe -> RecipeProcess(
                recipeId = recipeId,
                type = type,
                outputs = listOf(outputStack),
                inputs = ingredients(recipe.ingredients),
                special = recipe.isSpecial,
            )

            is AbstractCookingRecipe -> RecipeProcess(
                recipeId = recipeId,
                type = type,
                outputs = listOf(outputStack),
                inputs = ingredients(recipe.ingredients),
                special = recipe.isSpecial,
                experience = recipe.experience,
                cookTime = recipe.cookingTime,
            )

            is SingleItemRecipe -> RecipeProcess(
                recipeId = recipeId,
                type = type,
                outputs = listOf(outputStack),
                inputs = ingredients(recipe.ingredients),
                special = recipe.isSpecial,
            )

            else -> RecipeProcess(
                recipeId = recipeId,
                type = type,
                outputs = listOf(outputStack),
                inputs = ingredients(recipe.ingredients),
                special = recipe.isSpecial,
            )
        }
    }

    private fun shapedRecipeProcess(
        recipeId: String,
        type: String,
        recipe: ShapedRecipe,
        output: RecipeStack,
    ): RecipeProcess {
        val signatureToSymbol = LinkedHashMap<String, String>()
        val key = LinkedHashMap<String, RecipeIngredient>()
        val pattern = mutableListOf<String>()
        val ingredients = recipe.ingredients
        val width = recipe.width
        val height = recipe.height

        for (y in 0 until height) {
            val row = StringBuilder()
            for (x in 0 until width) {
                val ingredient = ingredients[y * width + x]
                if (ingredient.isEmpty) {
                    row.append(' ')
                    continue
                }
                val recipeIngredient = ingredient(ingredient)
                val signature = recipeIngredient.toString()
                val symbol = signatureToSymbol.getOrPut(signature) { symbolFor(signatureToSymbol.size) }
                key.putIfAbsent(symbol, recipeIngredient)
                row.append(symbol)
            }
            pattern += row.toString()
        }

        return RecipeProcess(
            recipeId = recipeId,
            type = type,
            outputs = listOf(output),
            inputs = key.values.toList(),
            shape = RecipeShape(pattern, key),
            special = recipe.isSpecial,
        )
    }

    private fun ingredients(ingredients: Iterable<Ingredient>): List<RecipeIngredient> {
        return ingredients.mapNotNull {
            if (it.isEmpty) null else ingredient(it).takeUnless { recipeIngredient -> recipeIngredient.isEmpty }
        }
    }

    private fun ingredient(ingredient: Ingredient): RecipeIngredient {
        val items = mutableListOf<RecipeStack>()
        val tags = mutableListOf<RecipeItemTag>()
        if (!ingredient.isCustom) {
            for (value in ingredient.values) {
                when (value) {
                    is Ingredient.TagValue -> tags += itemTag(value.tag())
                    is Ingredient.ItemValue -> items += recipeStack(value.item())
                }
            }
        }
        if (tags.isEmpty() && items.isEmpty()) {
            for (itemStack in ingredient.items) {
                if (items.size >= MAX_DIRECT_ITEMS) break
                items += recipeStack(itemStack)
            }
        }
        return RecipeIngredient(items = items.distinctBy { it.itemId to it.count }, tags = tags.distinctBy { it.tagId })
    }

    private fun itemTag(tag: net.minecraft.tags.TagKey<net.minecraft.world.item.Item>): RecipeItemTag {
        val examples = mutableListOf<RecipeStack>()
        var candidateCount = 0
        for (holder in BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            candidateCount++
            if (examples.size < MAX_TAG_EXAMPLES) {
                examples += recipeStack(ItemStack(holder))
            }
        }
        return RecipeItemTag(
            tagId = tag.location().toString(),
            candidateCount = candidateCount,
            examples = examples,
        )
    }

    private fun recipeStack(stack: ItemStack): RecipeStack {
        val itemId = if (stack.isEmpty) {
            "minecraft:air"
        } else {
            stack.item.resId.toString()
        }
        return RecipeStack(
            itemId = itemId,
            count = stack.count,
            remainder = stack.remainderStack(),
        )
    }

    private fun ItemStack.remainderStack(): RecipeStack? {
        if (isEmpty || !hasCraftingRemainingItem()) return null
        val remainder = craftingRemainingItem
        if (remainder.isEmpty) return null
        return RecipeStack(
            itemId = remainder.item.resId.toString(),
            count = remainder.count,
        )
    }

    private fun symbolFor(index: Int): String {
        check(index < SHAPE_SYMBOLS.length) { "shaped配方ingredient种类过多，无法分配单字符key" }
        return SHAPE_SYMBOLS[index].toString()
    }
}
