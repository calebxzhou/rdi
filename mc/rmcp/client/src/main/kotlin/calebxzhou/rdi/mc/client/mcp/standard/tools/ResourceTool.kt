package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.client.mcp.McpResourceIndex
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpToolResult
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.ResIdResolveQ

object ResourceTool {
    val all: List<StandardMcpTool> = listOf(ResIdResolveTool)
}

private object ResIdResolveTool : TypedMcpTool<ResIdResolveQ>(
    ResIdResolveQ.serializer(),
    ResIdResolveQ::class,
) {
    private val availableKinds = setOf("item", "block", "item_tag", "block_tag")

    override val description = """
        Resolve player text into exact Minecraft resource ids.
        Use this before calling APIs that require exact ids when the user gives fuzzy names or localized names.
        Response contains the best matching resource ids and their kind.
    """.trimIndent()

    override fun callTyped(req: ResIdResolveQ, game: McpGameInterface): Result<StandardMcpToolResult> {
        return runCatching {
            val text = req.text.trim().takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("text is blank")
            val kinds = req.kinds
                .flatMapTo(mutableSetOf()) {
                    when (val kind = it.lowercase()) {
                        "tag", "tags" -> listOf("item_tag", "block_tag")
                        else -> listOf(kind)
                    }
                }
                .orEmpty()
            val badKinds = kinds - availableKinds
            if (badKinds.isNotEmpty()) {
                throw IllegalArgumentException("invalid resource kind ${badKinds.joinToString()}")
            }
            McpResourceIndex.resolve(text, kinds, limit = 10)
        }.map { result ->
            StandardMcpToolResult.text(result.toString())
        }
    }
}
