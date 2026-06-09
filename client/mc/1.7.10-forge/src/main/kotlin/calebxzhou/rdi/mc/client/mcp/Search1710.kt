package calebxzhou.rdi.mc.client.mcp

import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.StatCollector

object Search1710 {
    fun refreshResourceIndex() {
        val entries = mutableListOf<McpResourceIndexEntry>()
        Block.blockRegistry.getKeys()
            .asSequence()
            .mapNotNull { it?.toString() }
            .forEach { id ->
                val block = Block.blockRegistry.getObject(id) as? Block ?: return@forEach
                entries += McpResourceIndexEntry("block", canonicalId(id), searchName(id, block.getLocalizedName()))
            }
        Item.itemRegistry.getKeys()
            .asSequence()
            .mapNotNull { it?.toString() }
            .forEach { id ->
                val item = Item.itemRegistry.getObject(id) as? Item ?: return@forEach
                entries += McpResourceIndexEntry("item", canonicalId(id), searchName(id, itemDisplayName(item)))
            }
        McpResourceIndex.refresh(entries)
    }

    private fun itemDisplayName(item: Item): String {
        return runCatching { ItemStack(item, 1, 0).getDisplayName() }
            .getOrElse {
                val key = item.getUnlocalizedName()
                StatCollector.translateToLocal("$key.name").takeIf { translated -> translated != "$key.name" } ?: key
            }
    }

    private fun searchName(id: String, localizedName: String): String {
        val path = id.substringAfter(':')
        return listOf(localizedName, path.replace('_', ' '), path)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
    }

    private fun canonicalId(id: String): String =
        if (id.contains(":")) id else "minecraft:$id"
}
