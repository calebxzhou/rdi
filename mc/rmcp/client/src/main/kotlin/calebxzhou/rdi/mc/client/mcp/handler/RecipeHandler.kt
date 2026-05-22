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

private fun McpHttpContext.intParam(key: String, default: Int): Int {
    val text = paramNull(key) ?: return default
    return text.toIntOrNull() ?: throw McpBadRequestError("$key must be integer")
}

private fun McpHttpContext.booleanParam(key: String, default: Boolean): Boolean {
    return when (paramNull(key)?.trim()?.lowercase()) {
        null -> default
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> throw McpBadRequestError("$key must be boolean")
    }
}
