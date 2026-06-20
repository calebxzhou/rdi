package calebxzhou.rdi.mc.client.mcp

import io.fusionauth.http.HTTPMethod

interface McpTypedHandler {
    val method: HTTPMethod get() = HTTPMethod.POST
    val helpDoc: String
        get() = "help doc unavailable"

    fun handle(ctx: McpHttpContext): Result<Any?>
}
