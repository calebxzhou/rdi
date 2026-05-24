package calebxzhou.rdi.mc.common3

import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * calebxzhou @ 2026-05-23 12:01
 */
val mc
    get() = Minecraft.getInstance()

val Block.resId: ResourceLocation
    get() = BuiltInRegistries.BLOCK.getKey(this)
val Item.resId: ResourceLocation
    get() = BuiltInRegistries.ITEM.getKey(this)
val TagKey<Item>.tagItems: Iterable<Holder<Item>>
    get() = BuiltInRegistries.ITEM.getTagOrEmpty(this)
val TagKey<Item>.tagItemIds: MutableSet<String>
    get() = tagItems.mapTo(mutableSetOf()) { it.value().resId.toString() }
fun String.parseResId(): ResourceLocation? =
    ResourceLocation.tryParse(this)
fun ResourceLocation.makeItemTagKey(): TagKey<Item> =
    TagKey.create(Registries.ITEM,this)
val ResourceLocation.isBlock
    get() = BuiltInRegistries.BLOCK.containsKey(this)
fun ResourceLocation.resolveBlock(): Block? =
    if(isBlock) BuiltInRegistries.BLOCK.get(this) else null