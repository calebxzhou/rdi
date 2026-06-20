package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.RecipeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeResolution
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeToolQ

object RecipeTool {
    val all: List<StandardMcpTool> = listOf(
        RecipeListTool,
        RecipeTreeTool,
    )
}

private object RecipeListTool : TypedMcpTool<RecipeQ>(
    RecipeQ.serializer(),
    RecipeQ::class,
) {
    override val name = "recipe"
    override val description = """
        List possible single-step recipe processes for one or more output item ids.
        Use res_id_resolve first when the user gives fuzzy item names.
        Response shows direct recipe methods only, not a recursive material tree.
    """.trimIndent()

    override fun callTyped(req: RecipeQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            StandardMcpToolResult.text(game.recipes(req).getOrThrow())
        }
    }
}

private object RecipeTreeTool : TypedMcpTool<RecipeTreeToolQ>(
    RecipeTreeToolQ.serializer(),
    RecipeTreeToolQ::class,
) {
    override val description = """
        Build a compact recipe roadmap for crafting an output item recursively.
        Response is a text roadmap and unresolved/material summary for LLM planning.
    """.trimIndent()

    override fun callTyped(req: RecipeTreeToolQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val outputItemId = req.outputItemId.trim().takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("outputItemId is blank")
            require(req.outputCount > 0) { "outputCount must be positive" }
            require(req.maxDepth in 0..32) { "maxDepth must be 0..32" }
            val recipeId = req.recipeId?.trim()?.takeIf { it.isNotEmpty() }
            val ingredientItemId = req.ingredientItemId?.trim()?.takeIf { it.isNotEmpty() }
            val resolution = if (recipeId == null && ingredientItemId == null) null else RecipeResolution(
                outputItemId = outputItemId,
                recipeId = recipeId,
                ingredientItemId = ingredientItemId,
            )
            RecipeTreeQ(
                outputItemId = outputItemId,
                outputCount = req.outputCount,
                maxDepth = req.maxDepth,
                includeAlternatives = req.includeAlternatives,
                useInventory = req.useInventory,
                resolutions = listOfNotNull(resolution),
            )
        }.mapCatching { req ->
            StandardMcpToolResult.text(game.recipeTree(req).getOrThrow())
        }
    }
}
