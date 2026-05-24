package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import io.fusionauth.http.HTTPMethod

object ModListHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        List all loaded mod ids in the current game instance.
        No query params.
        Response is a space separated list of mod ids. Use these ids with mod info or to reason about available mod features.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.modIds()
    }
}

object ModInfoHandler : McpTypedHandler {
    private val idRegex = Regex("[a-z0-9_.-]+")

    override val method = HTTPMethod.GET
    override val helpDoc = """
        Read metadata for one loaded mod.
        Query params:
        id: required exact mod id, such as minecraft, neoforge, create, or jei.
        Response includes mod id, display name, version, description, and dependency metadata.
        Use mod list first if you do not know the exact mod id.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val id = ctx.param("id").trim()
        if (!idRegex.matches(id)) {
            throw McpBadRequestError("invalid mod id $id")
        }
        return ctx.game.modInfo(id)
    }
}
