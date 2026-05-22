package calebxzhou.rdi.mc.common2.mcp.model

import java.util.IdentityHashMap

object RecipeTextView {
    fun render(
        tree: RecipeTreeP,
        tagInventoryMatches: Map<String, List<String>> = emptyMap(),
    ): String = buildString {
        val aliases = tree.root?.let(::buildAliases) ?: AliasBook.EMPTY
        appendLine("target ${tree.goal}")
        if (tree.totalCost.isNotEmpty()) {
            appendLine("materials ${tree.totalCost.formatCosts()}")
        }
        if (aliases.list.isNotEmpty()) {
            appendLine("nodes")
            aliases.list.forEach { alias ->
                appendLine("${alias.id} ${alias.node.stack}")
            }
        }
        appendLine("roadmap")
        tree.root?.let { appendRoadmap(it, aliases, tagInventoryMatches) }
        if (tree.leftovers.isNotEmpty()) {
            appendLine("leftovers ${tree.leftovers.formatCosts()}")
        }
        tree.unresolved.forEach {
            appendLine("unresolved ${it.stack} reason=${it.reason}")
        }
    }.trimEnd()

    private fun StringBuilder.appendRoadmap(
        node: RecipeTreeNode,
        aliases: AliasBook,
        tagInventoryMatches: Map<String, List<String>>,
    ) {
        node.children.forEach { child -> appendRoadmap(child, aliases, tagInventoryMatches) }
        val process = node.process ?: return
        val inputs = node.children.takeIf { it.isNotEmpty() }
            ?.joinToString(" + ") { aliases.text(it) }
            ?: "?"
        append(inputs)
            .append(" --")
            .append(process.type)
            .append(
                RecipeTextFormatter.attributes(
                    process = process,
                    tagInventoryMatches = tagInventoryMatches,
                    batches = node.batches,
                    //out = node.outputPerBatch,
                )
            )
            .append("--> ")
            .appendLine(aliases.text(node))
    }

    private fun List<RecipeCost>.formatCosts(): String {
        return joinToString(" + ") { it.toString() }
    }

    private fun buildAliases(root: RecipeTreeNode): AliasBook {
        val list = mutableListOf<NodeAlias>()
        val byNode = IdentityHashMap<RecipeTreeNode, String>()
        fun visit(node: RecipeTreeNode) {
            node.children.forEach(::visit)
            if (node !== root && node.process != null) {
                val id = "N${list.size + 1}"
                byNode[node] = id
                list += NodeAlias(id, node)
            }
        }
        visit(root)
        return AliasBook(list, byNode)
    }

    private data class NodeAlias(
        val id: String,
        val node: RecipeTreeNode,
    )

    private data class AliasBook(
        val list: List<NodeAlias>,
        val byNode: IdentityHashMap<RecipeTreeNode, String>,
    ) {
        fun text(node: RecipeTreeNode): String {
            return byNode[node] ?: node.stack.toString()
        }

        companion object {
            val EMPTY = AliasBook(emptyList(), IdentityHashMap<RecipeTreeNode, String>())
        }
    }
}

object RecipeProcessTextView {
    fun render(
        items: List<String>,
        recipesByItem: Map<String, List<RecipeProcess>>,
        tagInventoryMatches: Map<String, List<String>> = emptyMap(),
    ): String = buildString {
        items.forEachIndexed { itemIndex, itemId ->
            if (itemIndex > 0) appendLine()
            appendLine("item $itemId")
            val recipes = recipesByItem[itemId].orEmpty()
            if (recipes.isEmpty()) {
                appendLine("unresolved reason=no_recipe")
            } else {
                recipes.forEachIndexed { recipeIndex, process ->
                    append(recipeIndex + 1)
                        .append(". ")
                        .appendLine(process.edgeText(tagInventoryMatches))
                }
            }
        }
    }.trimEnd()

    private fun RecipeProcess.edgeText(tagInventoryMatches: Map<String, List<String>>): String {
        val inputs = ingredientAmounts(tagInventoryMatches)
        val outputsText = (outputs + fluidOutputs).joinToString(" + ").ifBlank { "?" }
        return buildString {
            append(inputs)
                .append(" --")
                .append(type)
                .append(
                    RecipeTextFormatter.attributes(
                        process = this@edgeText,
                        tagInventoryMatches = tagInventoryMatches,
                        //out = this@edgeText.outputs.singleOrNull()?.count,
                    )
                )
                .append("--> ")
                .append(outputsText)
        }
    }

    private fun RecipeProcess.ingredientAmounts(tagInventoryMatches: Map<String, List<String>>): String {
        val recipeShape = shape ?: return inputs
            .joinToString(" + ") { RecipeTextFormatter.ingredientText(it, tagInventoryMatches) }
            .ifBlank { "?" }
        val counts = linkedMapOf<String, Int>()
        recipeShape.pattern.forEach { row ->
            row.forEach { symbol ->
                if (symbol != ' ') {
                    val key = symbol.toString()
                    val ingredientCount = recipeShape.key[key]?.count ?: 1
                    counts[key] = counts.getOrDefault(key, 0) + ingredientCount
                }
            }
        }
        return counts.map { (symbol, count) -> "${count}x $symbol" }
            .joinToString(" + ")
            .ifBlank { "?" }
    }
}

object RecipeTextFormatter {
    private const val MAX_INLINE_CHOICES = 4
    private val SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun attributes(
        process: RecipeProcess,
        tagInventoryMatches: Map<String, List<String>>,
        batches: Int? = null,
    ): String {
        val attributes = mutableListOf("recipe=${process.recipeId}")
        batches?.let { attributes += "batches=$it" }
        attributes += layoutAttributes(process, tagInventoryMatches)
        process.experience?.let { attributes += "xp=$it" }
        process.cookTime?.let { attributes += "time=${it}t" }
        return attributes.joinToString(",", prefix = "[", postfix = "]")
    }

    fun ingredientText(
        ingredient: RecipeIngredient,
        tagInventoryMatches: Map<String, List<String>>,
    ): String {
        return ingredient.recipeText(tagInventoryMatches)
    }

    fun RecipeIngredient.recipeText(tagInventoryMatches: Map<String, List<String>>): String {
        val parts = mutableListOf<String>()
        parts += items.choiceText { it.itemId }
        parts += tags.map { tag ->
            val matches = tagInventoryMatches[tag.tagId].orEmpty().choiceText { it }
            buildString {
                append("#").append(tag.tagId)
                append(" inv=")
                append(matches.joinToString("|").ifBlank { "none" })
            }
        }
        parts += fluids.choiceText { "${it.amount}mb:${it.fluidId}" }
        val text = parts.joinToString("|").ifBlank { "?" }
        return if (count == 1) text else "${count}x $text"
    }

    private fun layoutAttributes(
        process: RecipeProcess,
        tagInventoryMatches: Map<String, List<String>>,
    ): List<String> {
        process.shape?.let { shape ->
            return listOf("shape=${shape.pattern.joinToString("|")}") +
                shape.key.map { (symbol, ingredient) -> "$symbol=${ingredient.recipeText(tagInventoryMatches)}" }
        }
        if (process.type.contains("crafting_shapeless")) {
            return listOf("layout=shapeless") +
                process.inputs.mapIndexed { index, ingredient ->
                    "${symbol(index)}=${ingredient.recipeText(tagInventoryMatches)}"
                }
        }
        return emptyList()
    }

    private fun <T> List<T>.choiceText(text: (T) -> String): List<String> {
        if (isEmpty()) return emptyList()
        val visible = take(MAX_INLINE_CHOICES).map(text)
        val hidden = size - visible.size
        return if (hidden > 0) visible + "...(+$hidden)" else visible
    }

    private fun symbol(index: Int): String {
        return if (index < SYMBOLS.length) SYMBOLS[index].toString() else "I$index"
    }
}
