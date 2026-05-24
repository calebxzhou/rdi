package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpResourceIndex
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import io.fusionauth.http.HTTPMethod

object ResIdResolveHandler : McpTypedHandler {
    private val availableKinds = setOf("item", "block", "item_tag", "block_tag")

    override val method = HTTPMethod.GET

    override val helpDoc = """
        Resolve player text into exact Minecraft resource ids.
        Query params:
        text: required search text, such as "oak plank", "create seat", "wood slab".
        kinds: optional space/comma separated filter. Allowed: item, block, item_tag, block_tag. "tag" means item_tag and block_tag.
        Use this before calling APIs that require exact ids when the user gives fuzzy names or localized names.
        Response contains the best matching resource ids and their kind.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> = runCatching {
        val text = ctx.param("text").trim()
        val limit = 10
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
