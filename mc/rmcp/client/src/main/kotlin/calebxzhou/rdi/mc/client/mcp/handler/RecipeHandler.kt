package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.ktutils.std.spaceSplit
import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.model.RecipeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeResolution
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import io.fusionauth.http.HTTPMethod

object RecipeHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        List possible single-step recipe processes for one or more output item ids.
        Query params:
        items: required space separated exact item ids, such as "minecraft:stick create:andesite_alloy".
        includeHidden: optional boolean. Default false.
        Use res-id-resolve first when the user gives fuzzy item names.
        Response shows direct recipe methods only, not a recursive material tree.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val items = ctx.param("items")
            .spaceSplit()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?: throw McpBadRequestError("items is blank")
        return ctx.game.recipes(
            RecipeQ(
                items = items,
                includeHidden = ctx.booleanParam("includeHidden", false),
            )
        )
    }
}

object RecipeTreeHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Build a compact recipe roadmap for crafting an output item recursively.
        Query params:
        outputItemId: required exact item id to craft.
        outputCount: optional target count. Default 1 and must be positive.
        maxDepth: optional recursion depth from 0 to 32. Default 8.
        includeAlternatives: optional boolean. Default false.
        useInventory: optional boolean. Default true, allowing current inventory to satisfy needed materials.
        recipeId: optional selected recipe id for this output when multiple recipes exist.
        ingredientItemId: optional selected ingredient item id for this output when a recipe input accepts alternatives.
        Response is a text roadmap and unresolved/material summary for LLM planning.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val outputItemId = ctx.param("outputItemId").trim().takeIf { it.isNotEmpty() }
            ?: throw McpBadRequestError("outputItemId is blank")
        val outputCount = ctx.intParam("outputCount", 1).takeIf { it > 0 }
            ?: throw McpBadRequestError("outputCount must be positive")
        val maxDepth = ctx.intParam("maxDepth", 8).takeIf { it in 0..32 }
            ?: throw McpBadRequestError("maxDepth must be 0..32")
        val resolution = recipeResolution(ctx, outputItemId)
        return ctx.game.recipeTree(
            RecipeTreeQ(
                outputItemId = outputItemId,
                outputCount = outputCount,
                maxDepth = maxDepth,
                includeAlternatives = ctx.booleanParam("includeAlternatives", false),
                useInventory = ctx.booleanParam("useInventory", true),
                resolutions = listOfNotNull(resolution),
            )
        )
    }

    private fun recipeResolution(ctx: McpHttpContext, outputItemId: String): RecipeResolution? {
        val recipeId = ctx.paramNull("recipeId")?.trim()?.takeIf { it.isNotEmpty() }
        val ingredientItemId = ctx.paramNull("ingredientItemId")?.trim()?.takeIf { it.isNotEmpty() }
        if (recipeId == null && ingredientItemId == null) return null
        return RecipeResolution(
            outputItemId = outputItemId,
            recipeId = recipeId,
            ingredientItemId = ingredientItemId,
        )
    }
}
