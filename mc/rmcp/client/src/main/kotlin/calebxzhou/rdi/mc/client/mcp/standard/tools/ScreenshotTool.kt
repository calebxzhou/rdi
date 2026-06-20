package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import kotlinx.serialization.json.JsonObject
import java.util.Base64

object ScreenshotTool {
    val all: List<StandardMcpTool> = listOf(ScreenshotPngTool)
}

private object ScreenshotPngTool : StandardMcpTool {
    override val name = "screenshot"
    override val description = "Read the current visual game frame as a PNG image."

    override fun call(params: JsonObject, game: McpGameInterface): Result<StandardMcpToolResult> {
        return game.screenshotPngData().map { png ->
            StandardMcpToolResult.imagePng(Base64.getEncoder().encodeToString(png))
        }
    }
}
