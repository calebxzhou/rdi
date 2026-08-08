package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.client.mcp.McpResourceIndex
import calebxzhou.rdi.mc.client.mcp.McpResourceIndexEntry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.locale.Language
import net.minecraft.world.item.ItemStack

object Search {
    fun refreshResourceIndex() {
        val languageData = Language.getInstance().languageData
        val entries = mutableListOf<McpResourceIndexEntry>()
        BuiltInRegistries.BLOCK.forEach { block ->
            val id = BuiltInRegistries.BLOCK.getKeyOrNull(block) ?: return@forEach
            entries += McpResourceIndexEntry(
                kind = "block",
                id = id.toString(),
                name = languageData.name(block.getDescriptionId(), id.path)
            )
        }
        BuiltInRegistries.ITEM.forEach { item ->
            val id = BuiltInRegistries.ITEM.getKeyOrNull(item) ?: return@forEach
            if (id.toString() == "minecraft:air") {
                return@forEach
            }
            val langKey = item.getDescriptionId(ItemStack(item))
            entries += McpResourceIndexEntry(
                kind = "item",
                id = id.toString(),
                name = languageData.name(langKey, id.path)
            )
        }
        BuiltInRegistries.BLOCK.getTagNames().forEach { tag ->
            val id = tag.location()
            entries += McpResourceIndexEntry("block_tag", id.toString(), id.path)
        }
        BuiltInRegistries.ITEM.getTagNames().forEach { tag ->
            val id = tag.location()
            entries += McpResourceIndexEntry("item_tag", id.toString(), id.path)
        }
        McpResourceIndex.refresh(entries)
    }

    private fun Map<String, String>.name(key: String, fallback: String): String {
        return get(key)?.trim()?.takeIf { it.isNotEmpty() && it != key } ?: fallback
    }
}