package calebxzhou.rdi.mc.common2.mcp.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2026-05-18 13:28
 */

@Serializable
data class RecipeStack(
    val itemId: String,
    val count: Int = 1,
    val chance: Float = 1f,
    val remainder: RecipeStack? = null,
) {
    override fun toString() = buildString {
        if (chance != 1f) append("~")
        append(count).append("x ").append(itemId)
        if (remainder != null) append(" -> ").append(remainder)
    }
}

@Serializable
data class RecipeFluidStack(
    val fluidId: String,
    val amount: Int,
    val chance: Float = 1f,
) {
    override fun toString() = buildString {
        if (chance != 1f) append("~")
        append(amount).append("mb ").append(fluidId)
    }
}

@Serializable
data class RecipeItemTag(
    val tagId: String,
    val count: Int = 1,
    val candidateCount: Int = 0,
    val examples: List<RecipeStack> = emptyList(),
) {
    override fun toString() = buildString {
        append(count).append("x #").append(tagId)
        if (candidateCount > 0) append(" candidates ").append(candidateCount)
        if (examples.isNotEmpty()) append(" examples ").append(examples.joinToString(", "))
    }
}

@Serializable
data class RecipeIngredient(
    val items: List<RecipeStack> = emptyList(),
    val fluids: List<RecipeFluidStack> = emptyList(),
    val tags: List<RecipeItemTag> = emptyList(),
    val count: Int = 1,
    val chance: Float = 1f,
) {
    val isEmpty get() = items.isEmpty() && fluids.isEmpty() && tags.isEmpty()

    override fun toString() = buildString {
        if (chance != 1f) append("~")
        append(count).append("x ")
        append((items + fluids + tags).joinToString("|"))
    }
}

@Serializable
data class RecipeShape(
    val pattern: List<String>,
    val key: Map<String, RecipeIngredient>,
) {
    override fun toString() = buildString {
        appendLine("pattern")
        pattern.forEach { appendLine(it) }
        appendLine("key")
        key.forEach { (symbol, ingredient) -> appendLine("$symbol $ingredient") }
    }.trimEnd()
}

@Serializable
data class RecipeProcess(
    val recipeId: String,
    val type: String,
    val outputs: List<RecipeStack> = emptyList(),
    val fluidOutputs: List<RecipeFluidStack> = emptyList(),
    val inputs: List<RecipeIngredient> = emptyList(),
    val catalysts: List<RecipeIngredient> = emptyList(),
    val renderOnly: List<RecipeIngredient> = emptyList(),
    val shape: RecipeShape? = null,
    val special: Boolean = false,
    val experience: Float? = null,
    val cookTime: Int? = null,
) {
    override fun toString() = buildString {
        append(recipeId).append(" ").append(type)
        if (outputs.isNotEmpty() || fluidOutputs.isNotEmpty()) {
            append(" -> ").append((outputs + fluidOutputs).joinToString(", "))
        }
        appendLine()
        shape?.let {
            appendLine(it)
        } ?: inputs.forEachIndexed { index, ingredient ->
            appendLine("input$index $ingredient")
        }
        if (catalysts.isNotEmpty()) {
            appendLine("catalysts")
            catalysts.forEach { appendLine(it) }
        }
        if (renderOnly.isNotEmpty()) {
            appendLine("render_only")
            renderOnly.forEach { appendLine(it) }
        }
    }.trimEnd()
}

@Serializable
data class RecipeResolution(
    val outputItemId: String,
    val recipeId: String? = null,
    val ingredientItemId: String? = null,
) {
    override fun toString() = buildString {
        append(outputItemId)
        recipeId?.let { append(" recipe ").append(it) }
        ingredientItemId?.let { append(" item ").append(it) }
    }
}

@Serializable
data class RecipeQ(
    @McpParam("Exact output item ids.", minItems = 1)
    val items: List<String>,
    @McpParam("Default false.")
    val includeHidden: Boolean = false,
)

@Serializable
enum class RecipeMaterialKind {
    ITEM,
    FLUID,
}

@Serializable
data class RecipeTreeQ(
    val outputItemId: String,
    val outputCount: Int = 1,
    val maxDepth: Int = 8,
    val includeAlternatives: Boolean = false,
    val useInventory: Boolean = true,
    val resolutions: List<RecipeResolution> = emptyList(),
)

@Serializable
data class RecipeTreeToolQ(
    @McpParam("Exact item id to craft.")
    val outputItemId: String,
    @McpParam("Target count. Default 1 and must be positive.", minimum = 1)
    val outputCount: Int = 1,
    @McpParam("Recursion depth from 0 to 32. Default 8.", minimum = 0, maximum = 32)
    val maxDepth: Int = 8,
    @McpParam("Default false.")
    val includeAlternatives: Boolean = false,
    @McpParam("Default true, allowing current inventory to satisfy needed materials.")
    val useInventory: Boolean = true,
    @McpParam("Selected recipe id for this output when multiple recipes exist.")
    val recipeId: String? = null,
    @McpParam("Selected ingredient item id for this output when a recipe input accepts alternatives.")
    val ingredientItemId: String? = null,
)

@Serializable
data class RecipeTreeP(
    val goal: RecipeStack,
    val root: RecipeTreeNode?,
    val totalCost: List<RecipeCost>,
    val leftovers: List<RecipeCost> = emptyList(),
    val unresolved: List<RecipeUnresolved> = emptyList(),
) {
    override fun toString() = RecipeTextView.render(this)
}

@Serializable
data class RecipeTreeNode(
    val stack: RecipeStack,
    val process: RecipeProcess? = null,
    val batches: Int = 1,
    val outputPerBatch: Int = 1,
    val children: List<RecipeTreeNode> = emptyList(),
    val unresolvedReason: String? = null,
) {
    fun text(depth: Int = 0): String = buildString {
        val indent = "  ".repeat(depth)
        append(indent).append(stack)
        process?.let { append(" via ").append(it.recipeId).append(" x").append(batches) }
        unresolvedReason?.let { append(" unresolved ").append(it) }
        appendLine()
        children.forEach { appendLine(it.text(depth + 1)) }
    }.trimEnd()
}

@Serializable
data class RecipeCost(
    val kind: RecipeMaterialKind,
    val id: String,
    val amount: Int,
    val chance: Float = 1f,
) {
    override fun toString() = buildString {
        if (chance != 1f) append("~")
        append(amount)
        append(if (kind == RecipeMaterialKind.FLUID) "mb " else "x ")
        append(id)
    }
}

@Serializable
data class RecipeUnresolved(
    val stack: RecipeStack,
    val reason: String,
) {
    override fun toString() = "${stack.itemId} $reason"
}

