package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import io.fusionauth.http.HTTPMethod

object ScreenshotHandler : McpTypedHandler {
    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        return ctx.game.screenshotPngData()
    }
}
