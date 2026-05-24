package calebxzhou.rdi.mc.client.mcp.handler

import calebxzhou.rdi.mc.client.mcp.McpHttpContext
import calebxzhou.rdi.mc.client.mcp.McpTypedHandler
import calebxzhou.rdi.mc.client.mcp.send
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.model.CraftQ

/**
 * calebxzhou @ 2026-05-23 17:27
 */
object CraftHandler : McpTypedHandler {
    override val helpDoc = """
        Craft an item using an explicit crafting pattern and player inventory slots.
        Request body is YAML.
        Required fields:
        pattern: crafting grid rows separated by "|". Use letters for ingredients, such as "AA|BB" or "ABC|DEF|GHI".
        key: map from pattern letter to player inventory slot id, such as A: 5.
        Optional fields:
        times: number of crafting batches. Default is 1 and must be positive.
        test: true previews consumed slots and result without changing inventory.
        The server finds the matching Minecraft recipe from the supplied pattern and slot items.
    """.trimIndent()

    override fun handle(ctx: McpHttpContext): Result<Any?> {
        val req = ctx.ymlBody<CraftQ>()
        if (req.pattern.isBlank()) {
            throw McpBadRequestError("pattern is blank")
        }
        if (req.key.isEmpty()) {
            throw McpBadRequestError("key is empty")
        }
        if (req.times <= 0) {
            throw McpBadRequestError("times must be positive")
        }
        return ctx.game.send(req)
    }

}
