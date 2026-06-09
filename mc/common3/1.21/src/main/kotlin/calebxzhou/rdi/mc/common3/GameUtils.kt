package calebxzhou.rdi.mc.common3

import com.mojang.datafixers.functions.Functions.comp
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * calebxzhou @ 2026-05-23 12:01
 */
val mc
    get() = Minecraft.getInstance()
lateinit var mcs: MinecraftServer
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

operator fun MutableComponent.plus(component: Component): MutableComponent = this.append(component)
fun ServerPlayer.sendMessage(text: String) {
        sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GOLD))

}