package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import io.fusionauth.http.HTTPMethod

object PlayerHandler : McpTypedHandler {
    override val method = HTTPMethod.GET
    override val helpDoc = """
        Read the local player's current state.
        No query params.
        Response includes player name, uuid, dimension, position, health, food, and game mode.
        Use the returned position when planning nearby block, container, or movement actions.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.playerInfo()
    }
}
