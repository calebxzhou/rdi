package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.ModInfoQ
import kotlinx.serialization.json.JsonObject

object ModTool {
    val all: List<StandardMcpTool> = listOf(
        ModListTool,
        ModInfoTool,
    )
}

private object ModListTool : StandardMcpTool {
    override val description = """
        List all loaded mod ids in the current game instance.
        Response is a space separated list of mod ids. Use these ids with mod info or to reason about available mod features.
    """.trimIndent()

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return game.modIds().map { text -> StandardMcpToolResult.text(text) }
    }
}

private object ModInfoTool : TypedMcpTool<ModInfoQ>(
    ModInfoQ.serializer(),
    ModInfoQ::class,
) {
    private val idRegex = Regex("[a-z0-9_.-]+")

    override val description = """
        Read metadata for one loaded mod.
        Response includes mod id, display name, version, description, and dependency metadata.
        Use mod list first if you do not know the exact mod id.
    """.trimIndent()

    override fun callTyped(req: ModInfoQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val id = req.id.trim().takeIf { idRegex.matches(it) }
                ?: throw IllegalArgumentException("invalid mod id")
            val info = game.modInfo(id).getOrThrow()
            StandardMcpToolResult.text(info.toString())
        }
    }
}
