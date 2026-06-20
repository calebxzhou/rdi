package calebxzhou.rdi.mc.client.mcp.standard.tools

import calebxzhou.rdi.mc.client.mcp.standard.StandardMcpTool
import calebxzhou.rdi.mc.client.mcp.standard.TypedMcpTool
import calebxzhou.rdi.mc.common2.mcp.model.CraftQ

object CraftTool {
    val all: List<StandardMcpTool> = listOf(CraftItemTool)
}

private object CraftItemTool : TypedMcpTool<CraftQ>(
    CraftQ.serializer(),
    CraftQ::class,
) {
    override val name = "craft"
    override val readOnly = false
    override val destructive = true
    override val description = """
        Craft an item using an explicit crafting pattern and player inventory slots.
        No need crafting table.
        No need crafting table.
        The server finds the matching Minecraft recipe from the supplied pattern and slot items.
    """.trimIndent()
}
