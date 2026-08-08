package calebxzhou.rdi.mc.client.mcpimpl

import calebxzau.mc.common2021.resId
import calebxzhou.rdi.mc.common2.mcp.model.RecipeFluidStack
import calebxzhou.rdi.mc.common2.mcp.model.RecipeIngredient
import calebxzhou.rdi.mc.common2.mcp.model.RecipeItemTag
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeShape
import calebxzhou.rdi.mc.common2.mcp.model.RecipeStack
import mezz.jei.api.gui.builder.IIngredientAcceptor
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.builder.IRecipeSlotBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback
import mezz.jei.api.gui.ingredient.IRecipeSlotTooltipCallback
import mezz.jei.api.gui.placement.HorizontalAlignment
import mezz.jei.api.gui.placement.VerticalAlignment
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory
import mezz.jei.api.ingredients.IIngredientRenderer
import mezz.jei.api.ingredients.IIngredientType
import mezz.jei.api.ingredients.ITypedIngredient
import mezz.jei.api.recipe.IFocusGroup
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.level.material.Fluid
import net.minecraftforge.fluids.FluidStack
import java.util.Optional

object JeiRecipeProcessCollector {
    private const val MAX_TAG_EXAMPLES = 3
    private val SHAPE_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun collect(runtime: IJeiRuntime): List<RecipeProcess> {
        val focusGroup = runtime.jeiHelpers.focusFactory.emptyFocusGroup
        val categories = runtime.recipeManager
            .createRecipeCategoryLookup()
            .get()
            .toList()
        return categories
            .filter { it.isProcessCategory() }
            .flatMap { category -> collectCategory(runtime, category, focusGroup) }
            .distinctBy { process -> process.signature() }
    }

    private fun IRecipeCategory<*>.isProcessCategory(): Boolean {
        val uid = recipeType.uid
        return uid.namespace != "jei" && !uid.path.startsWith("tag_recipes/")
    }

    @Suppress("UNCHECKED_CAST")
    private fun collectCategory(
        runtime: IJeiRuntime,
        category: IRecipeCategory<*>,
        focusGroup: IFocusGroup,
    ): List<RecipeProcess> {
        return collectTypedCategory(runtime, category as IRecipeCategory<Any>, focusGroup)
    }

    private fun <T : Any> collectTypedCategory(
        runtime: IJeiRuntime,
        category: IRecipeCategory<T>,
        focusGroup: IFocusGroup,
    ): List<RecipeProcess> {
        val recipeType = category.recipeType
        val recipes = runtime.recipeManager.createRecipeLookup(recipeType).get().toList()
        return recipes.mapIndexedNotNull { index, recipe ->
            runCatching {
                recipeProcess(category, recipe, index, focusGroup)
            }.getOrNull()
        }
    }

    private fun <T : Any> recipeProcess(
        category: IRecipeCategory<T>,
        recipe: T,
        index: Int,
        focusGroup: IFocusGroup,
    ): RecipeProcess? {
        if (!category.isHandled(recipe)) return null
        val layout = CapturingRecipeLayoutBuilder()
        category.setRecipe(layout, recipe, focusGroup)

        val recipeId = category.getRegistryName(recipe)?.toString() ?: generatedRecipeId(category, recipe, index)
        val type = recipe.recipeTypeId() ?: category.recipeType.uid.toString()
        val minecraftRecipe = recipe.minecraftRecipe()
        val inputSlots = layout.slots(RecipeIngredientRole.INPUT).withMinecraftIngredients(minecraftRecipe)
        val outputs = layout.slots(RecipeIngredientRole.OUTPUT).flatMap { it.itemStacks() }.distinctBy { it.itemId to it.count }
        val fluidOutputs = layout.slots(RecipeIngredientRole.OUTPUT).flatMap { it.fluidStacks() }.distinctBy { it.fluidId to it.amount }
        if (outputs.isEmpty() && fluidOutputs.isEmpty()) return null

        val shape = minecraftRecipe?.minecraftShape() ?: layout.shape(type, inputSlots)
        return RecipeProcess(
            recipeId = recipeId,
            type = type,
            outputs = outputs,
            fluidOutputs = fluidOutputs,
            inputs = shape?.key?.values?.toList() ?: inputSlots.mapNotNull { it.ingredient() },
            catalysts = layout.slots(RecipeIngredientRole.CATALYST).mapNotNull { it.ingredient() },
            renderOnly = layout.slots(RecipeIngredientRole.RENDER_ONLY).mapNotNull { it.ingredient() },
            shape = shape,
            special = minecraftRecipe?.isSpecial ?: false,
            experience = (minecraftRecipe as? AbstractCookingRecipe)?.experience,
            cookTime = (minecraftRecipe as? AbstractCookingRecipe)?.cookingTime,
        )
    }

    private fun <T : Any> generatedRecipeId(category: IRecipeCategory<T>, recipe: T, index: Int): String {
        return "${category.recipeType.uid}/jei/${recipe.javaClass.name.substringAfterLast('.')}/$index"
    }

    private fun Any.minecraftRecipe(): Recipe<*>? {
        return this as? Recipe<*>
    }

    private fun Any.recipeTypeId(): String? {
        val recipe = minecraftRecipe() ?: return null
        return BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.serializer)?.toString()
    }

    private fun Recipe<*>.minecraftShape(): RecipeShape? {
        return when (this) {
            is ShapedRecipe -> shapedRecipeShape(this)
            is ShapelessRecipe -> null
            else -> null
        }
    }

    private fun shapedRecipeShape(recipe: ShapedRecipe): RecipeShape {
        val signatureToSymbol = LinkedHashMap<String, String>()
        val key = LinkedHashMap<String, RecipeIngredient>()
        val pattern = mutableListOf<String>()
        for (y in 0 until recipe.height) {
            val row = StringBuilder()
            for (x in 0 until recipe.width) {
                val ingredient = recipe.ingredients[y * recipe.width + x]
                if (ingredient.isEmpty) {
                    row.append(' ')
                    continue
                }
                val recipeIngredient = ingredient.recipeIngredient()
                val signature = recipeIngredient.toString()
                val symbol = signatureToSymbol.getOrPut(signature) { symbolFor(signatureToSymbol.size) }
                key.putIfAbsent(symbol, recipeIngredient)
                row.append(symbol)
            }
            pattern += row.toString()
        }
        return RecipeShape(pattern, key)
    }

    private fun CapturingRecipeLayoutBuilder.shape(
        type: String,
        inputSlots: List<CapturedSlot>,
    ): RecipeShape? {
        if (!type.contains("crafting")) return null
        val slots = inputSlots.filter { !it.isEmpty && it.x != null && it.y != null }
        if (slots.size != inputSlots.count { !it.isEmpty }) return null
        if (slots.isEmpty()) return null

        val xs = slots.mapNotNull { it.x }.distinct().sorted()
        val ys = slots.mapNotNull { it.y }.distinct().sorted()
        val slotByPosition = slots.associateBy { it.x to it.y }
        val signatureToSymbol = LinkedHashMap<String, String>()
        val key = LinkedHashMap<String, RecipeIngredient>()
        val pattern = ys.map { y ->
            buildString {
                xs.forEach { x ->
                    val ingredient = slotByPosition[x to y]?.ingredient()
                    if (ingredient == null) {
                        append(' ')
                    } else {
                        val signature = ingredient.toString()
                        val symbol = signatureToSymbol.getOrPut(signature) { symbolFor(signatureToSymbol.size) }
                        key.putIfAbsent(symbol, ingredient)
                        append(symbol)
                    }
                }
            }
        }
        return RecipeShape(pattern, key)
    }

    private fun List<CapturedSlot>.withMinecraftIngredients(recipe: Recipe<*>?): List<CapturedSlot> {
        val ingredients = recipe?.ingredients
            ?.filterNot { it.isEmpty }
            ?.map { it.recipeIngredient() }
            ?: return this
        if (ingredients.size != count { !it.isEmpty }) return this
        var index = 0
        return map { slot ->
            if (slot.isEmpty) slot else slot.copy(minecraftIngredient = ingredients[index++])
        }
    }

    private fun Ingredient.recipeIngredient(): RecipeIngredient {
        val items = this.items.mapNotNull(::recipeStack).distinctBy { it.itemId to it.count }
        items.tagIngredient()?.let { return it }
        return RecipeIngredient(items = items)
    }

    private fun CapturedSlot.ingredient(): RecipeIngredient? {
        val items = itemStacks()
        val fluids = fluidStacks()
        minecraftIngredient?.let { ingredient ->
            if (ingredient.tags.isNotEmpty() || ingredient.items.size <= 1) return ingredient
            ingredient.items.tagIngredient()?.let {
                return it.copy(fluids = fluids.distinctBy { fluid -> fluid.fluidId to fluid.amount })
            }
            return ingredient
        }
        if (items.isEmpty() && fluids.isEmpty()) return null
        items.tagIngredient()?.let {
            return it.copy(fluids = fluids.distinctBy { fluid -> fluid.fluidId to fluid.amount })
        }
        return RecipeIngredient(
            items = items.distinctBy { it.itemId to it.count },
            fluids = fluids.distinctBy { it.fluidId to it.amount },
        )
    }

    private fun List<RecipeStack>.tagIngredient(): RecipeIngredient? {
        val items = distinctBy { it.itemId to it.count }
        if (items.size <= 1) return null
        val count = items.first().count
        if (items.any { it.count != count }) return null
        val itemIds = items.mapTo(linkedSetOf()) { it.itemId }
        val tag = matchingItemTag(itemIds) ?: return null
        return RecipeIngredient(tags = listOf(tag), count = count)
    }

    private fun matchingItemTag(itemIds: Set<String>): RecipeItemTag? {
        return BuiltInRegistries.ITEM.getTags()
            .map { pair ->
                val tag = pair.getFirst()
                val holders = pair.getSecond()
                val tagItemIds = holders.mapTo(linkedSetOf()) {
                    BuiltInRegistries.ITEM.getKey(it.value()).toString()
                }
                if (tagItemIds == itemIds) {
                    RecipeItemTag(
                        tagId = tag.location().toString(),
                        candidateCount = tagItemIds.size,
                        examples = holders.take(MAX_TAG_EXAMPLES).mapNotNull { recipeStack(ItemStack(it)) },
                    )
                } else {
                    null
                }
            }
            .filter { it != null }
            .map { it!! }
            .min(Comparator.comparingInt<RecipeItemTag> { it.tagId.length }.thenBy { it.tagId })
            .orElse(null)
    }

    private fun CapturedSlot.itemStacks(): List<RecipeStack> {
        return ingredients.mapNotNull { ingredient ->
            when (ingredient) {
                is ItemStack -> recipeStack(ingredient)
                else -> null
            }
        }
    }

    private fun CapturedSlot.fluidStacks(): List<RecipeFluidStack> {
        return ingredients.mapNotNull { ingredient ->
            when (ingredient) {
                is FluidStack -> ingredient.takeUnless { it.isEmpty }?.let {
                    RecipeFluidStack(BuiltInRegistries.FLUID.getKey(it.fluid).toString(), it.amount)
                }

                else -> null
            }
        } + fluids
    }

    private fun recipeStack(stack: ItemStack): RecipeStack? {
        if (stack.isEmpty) return null
        return RecipeStack(
            itemId = stack.item.resId.toString(),
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
        return if (index < SHAPE_SYMBOLS.length) SHAPE_SYMBOLS[index].toString() else "I$index"
    }

    private fun RecipeProcess.signature(): String {
        return listOf(
            type,
            recipeId,
            outputs.joinToString("|"),
            fluidOutputs.joinToString("|"),
            inputs.joinToString("|"),
        ).joinToString(" ")
    }

    private data class CapturedSlot(
        val role: RecipeIngredientRole,
        val x: Int? = null,
        val y: Int? = null,
        val ingredients: List<Any> = emptyList(),
        val fluids: List<RecipeFluidStack> = emptyList(),
        val minecraftIngredient: RecipeIngredient? = null,
    ) {
        val isEmpty get() = minecraftIngredient == null && ingredients.isEmpty() && fluids.isEmpty()
    }

    private class CapturingRecipeLayoutBuilder : IRecipeLayoutBuilder {
        private val slots = mutableListOf<CapturingRecipeSlotBuilder>()

        fun slots(role: RecipeIngredientRole): List<CapturedSlot> {
            return slots.filter { it.role == role }.map { it.build() }
        }

        override fun addSlot(role: RecipeIngredientRole): IRecipeSlotBuilder {
            return CapturingRecipeSlotBuilder(role).also { slots += it }
        }

        override fun addSlotToWidget(role: RecipeIngredientRole, widgetFactory: ISlottedWidgetFactory<*>): IRecipeSlotBuilder {
            return addSlot(role)
        }

        override fun addInvisibleIngredients(recipeIngredientRole: RecipeIngredientRole): IIngredientAcceptor<*> {
            return addSlot(recipeIngredientRole)
        }

        override fun moveRecipeTransferButton(posX: Int, posY: Int) = Unit

        override fun setShapeless() = Unit

        override fun setShapeless(posX: Int, posY: Int) = Unit

        override fun createFocusLink(vararg slots: IIngredientAcceptor<*>) = Unit
    }

    private class CapturingRecipeSlotBuilder(
        val role: RecipeIngredientRole,
    ) : IRecipeSlotBuilder {
        private var x: Int? = null
        private var y: Int? = null
        private var ingredients = mutableListOf<Any>()
        private val fluids = mutableListOf<RecipeFluidStack>()
        private var minecraftIngredient: RecipeIngredient? = null

        fun build(): CapturedSlot {
            return CapturedSlot(role, x, y, ingredients.toList(), fluids.toList(), minecraftIngredient)
        }

        override fun addIngredients(ingredient: Ingredient): IRecipeSlotBuilder {
            minecraftIngredient = ingredient.recipeIngredient()
            return this
        }

        override fun <I : Any?> addIngredients(ingredientType: IIngredientType<I>, ingredients: MutableList<I?>): IRecipeSlotBuilder {
            this.ingredients.addAll(ingredients.filterNotNull())
            return this
        }

        override fun <I : Any?> addIngredient(ingredientType: IIngredientType<I>, ingredient: I): IRecipeSlotBuilder {
            if (ingredient != null) ingredients += ingredient
            return this
        }

        override fun addIngredientsUnsafe(ingredients: MutableList<*>): IRecipeSlotBuilder {
            this.ingredients.addAll(ingredients.filterNotNull())
            return this
        }

        override fun addTypedIngredients(ingredients: MutableList<ITypedIngredient<*>>): IRecipeSlotBuilder {
            this.ingredients.addAll(ingredients.map { it.ingredient })
            return this
        }

        override fun addOptionalTypedIngredients(ingredients: MutableList<Optional<ITypedIngredient<*>>>): IRecipeSlotBuilder {
            this.ingredients.addAll(ingredients.mapNotNull { it.orElse(null)?.ingredient })
            return this
        }

        override fun addFluidStack(fluid: Fluid): IRecipeSlotBuilder {
            return addFluidStack(fluid, 1_000)
        }

        override fun addFluidStack(fluid: Fluid, amount: Long): IRecipeSlotBuilder {
            fluids += RecipeFluidStack(BuiltInRegistries.FLUID.getKey(fluid).toString(), amount.toInt())
            return this
        }

        override fun addFluidStack(fluid: Fluid, amount: Long, tag: CompoundTag): IRecipeSlotBuilder {
            return addFluidStack(fluid, amount)
        }

        override fun addTooltipCallback(tooltipCallback: IRecipeSlotTooltipCallback): IRecipeSlotBuilder = this

        override fun addRichTooltipCallback(tooltipCallback: IRecipeSlotRichTooltipCallback): IRecipeSlotBuilder = this

        override fun setSlotName(slotName: String): IRecipeSlotBuilder = this

        override fun setStandardSlotBackground(): IRecipeSlotBuilder = this

        override fun setOutputSlotBackground(): IRecipeSlotBuilder = this

        override fun setBackground(background: IDrawable, xOffset: Int, yOffset: Int): IRecipeSlotBuilder = this

        override fun setOverlay(overlay: IDrawable, xOffset: Int, yOffset: Int): IRecipeSlotBuilder = this

        override fun setFluidRenderer(capacity: Long, showCapacity: Boolean, width: Int, height: Int): IRecipeSlotBuilder = this

        override fun <T : Any?> setCustomRenderer(
            ingredientType: IIngredientType<T>,
            ingredientRenderer: IIngredientRenderer<T>,
        ): IRecipeSlotBuilder = this

        override fun setPosition(xPos: Int, yPos: Int): IRecipeSlotBuilder {
            x = xPos
            y = yPos
            return this
        }

        override fun setPosition(
            areaX: Int,
            areaY: Int,
            areaWidth: Int,
            areaHeight: Int,
            horizontalAlignment: HorizontalAlignment,
            verticalAlignment: VerticalAlignment,
        ): IRecipeSlotBuilder {
            return setPosition(
                areaX + horizontalAlignment.getXPos(areaWidth, width),
                areaY + verticalAlignment.getYPos(areaHeight, height),
            )
        }

        override fun getWidth(): Int = 16

        override fun getHeight(): Int = 16
    }
}
