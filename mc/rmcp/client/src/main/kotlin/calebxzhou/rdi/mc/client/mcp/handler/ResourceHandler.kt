package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpResourceIndex
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.common2.mcp.McpBadLimitError
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.McpMissingTextError
import io.fusionauth.http.HTTPMethod

object ResourceResolveHandler : McpTypedHandler {
    private val availableKinds = setOf("item", "block", "item_tag", "block_tag")

    override val method = HTTPMethod.GET

    override fun handle(ctx: McpHttpContext): Result<Any?> = runCatching {
        val text = ctx.paramNull("q")?.trim()?.takeIf { it.isNotEmpty() } ?: throw McpMissingTextError()
        val limit = ctx.paramNull("limit")?.let {
            it.toIntOrNull()?.takeIf { value -> value in 1..64 } ?: throw McpBadLimitError()
        } ?: 10
        val kinds = ctx.paramNull("kinds")
            ?.split(Regex("[,\\s]+"))
            ?.filter(String::isNotBlank)
            ?.flatMapTo(mutableSetOf()) {
                when (val kind = it.lowercase()) {
                    "tag", "tags" -> listOf("item_tag", "block_tag")
                    else -> listOf(kind)
                }
            }
            .orEmpty()
        val badKinds = kinds - availableKinds
        if (badKinds.isNotEmpty()) {
            throw McpBadRequestError("invalid resource kind ${badKinds.joinToString()}")
        }
        McpResourceIndex.resolve(text, kinds, limit)
    }
}
