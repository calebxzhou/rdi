package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import kotlinx.serialization.json.JsonObject

object PlayerTool {
    val all: List<StandardMcpTool> = listOf(PlayerInfoTool)
}

private object PlayerInfoTool : StandardMcpTool {
    override val name = "player"
    override val description = """
        Read the local player's current state.
        Response includes player name, uuid, dimension, position, health, food, and game mode.
        Use the returned position when planning nearby block, container, or movement actions.
    """.trimIndent()

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return game.playerInfo().map { player ->
            StandardMcpToolResult.text(player.toString())
        }
    }
}
