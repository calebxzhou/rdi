package calebxzhou.rdi.mc.client.mcpimpl211

import calebxzhou.rdi.mc.client.mc
import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.common2.mcp.McpBadSlotError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.McpServerMcpUnavailableError
import calebxzhou.rdi.mc.common2.mcp.McpServerTimeoutError
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlot
import calebxzhou.rdi.mc.common2.mcp.model.InventoryCompart
import calebxzhou.rdi.mc.common2.mcp.model.InventoryListP
import calebxzhou.rdi.mc.common2.mcp.model.InventorySlotQ
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.RecipeIngredient
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcessTextView
import calebxzhou.rdi.mc.common2.mcp.model.RecipeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTextView
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeP
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeNode
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.collections.set

object McpGameImpl : McpGameInterface {
    
    private const val RESPONSE_TIMEOUT_SECONDS = 30L

    private val pendingPacketMap = ConcurrentHashMap<String, CompletableFuture<McpS2CNetPacket>>()

    override fun send(packet: McpC2SNetPacket): Result<String> {
        return runCatching {
            val future = CompletableFuture<McpS2CNetPacket>()
            pendingPacketMap[packet.reqId] = future
            mc.execute {
                try {
                    PacketDistributor.sendToServer(McpNetPayload211.fromC2S(packet))
                } catch (e: Throwable) {
                    pendingPacketMap.remove(packet.reqId)?.completeExceptionally(e)
                }
            }
            future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).text
        }.recoverCatching { e ->
            throw when (e) {
                is TimeoutException -> McpServerTimeoutError()
                is ExecutionException -> McpServerMcpUnavailableError()
                else -> McpServerMcpUnavailableError()
            }
        }.also {
            pendingPacketMap.remove(packet.reqId)
        }
    }

    override fun inventory(): Result<InventoryListP> = runCatching {
        val inventory = mc.player?.inventory ?: throw McpNoPlayerError()
        InventoryListP(
            inv = inventory.items.mapIndexed(::slot),
            armor = inventory.armor.mapIndexed(::slot),
            offhand = inventory.offhand.mapIndexed(::slot),
        )
    }


    override fun inventorySlot(req: InventorySlotQ): Result<String> = runCatching {
        val player = mc.player ?: throw McpNoPlayerError()
        val stacks = when (req.compart) {
            InventoryCompart.INV -> player.inventory.items
            InventoryCompart.ARMOR -> player.inventory.armor
            InventoryCompart.OFFHAND -> player.inventory.offhand
        }
        val stack = stacks.getOrNull(req.slotId) ?: throw McpBadSlotError()
        val snbt = stack.saveOptional(player.registryAccess()).toString()
        "${stack}\n${snbt}"
    }

    override fun blockFind(req: BlockFindQ): Result<BlockFindP> = BlockMcpImpl.find(req)

    override fun recipes(req: RecipeQ): Result<String> = runCatching {
        if (!RecipeProcessIndex.isReady()) {
            return@runCatching "unresolved reason=jei_not_ready"
        }
        val recipesByItem = req.items.associateWith { RecipeProcessIndex.recipesByOutputItem(it) }
        val processes = recipesByItem.values.flatten()
        RecipeProcessTextView.render(req.items, recipesByItem, processes.tagInventoryMatches())
    }

    override fun recipeTree(req: RecipeTreeQ): Result<String> = runCatching {
        val tree = RecipeTreeResolver.resolve(req)
        RecipeTextView.render(tree, tree.tagInventoryMatches())
    }


    fun complete(packet: McpS2CNetPacket) {
        pendingPacketMap.remove(packet.reqId)?.complete(packet)
    }

    private fun slot(id: Int, stack: ItemStack): ContainerSlot {
        return if (stack.isEmpty) {
            ContainerSlot.empty(id)
        } else {
            ContainerSlot(id, BuiltInRegistries.ITEM.getKey(stack.item).toString(), stack.count)
        }
    }

    private fun RecipeTreeP.tagInventoryMatches(): Map<String, List<String>> {
        return processes().tagInventoryMatches()
    }

    private fun Iterable<RecipeProcess>.tagInventoryMatches(): Map<String, List<String>> {
        val player = mc.player ?: return emptyMap()
        val inventoryItemIds = (player.inventory.items + player.inventory.armor + player.inventory.offhand)
            .asSequence()
            .filterNot { it.isEmpty }
            .map { BuiltInRegistries.ITEM.getKey(it.item).toString() }
            .distinct()
            .toList()
        if (inventoryItemIds.isEmpty()) return emptyMap()

        val tagIds = linkedSetOf<String>()
        forEach { it.collectTagIds(tagIds) }
        if (tagIds.isEmpty()) return emptyMap()

        return tagIds.associateWith { tagId ->
            val location = ResourceLocation.tryParse(tagId) ?: return@associateWith emptyList()
            val tag = TagKey.create(Registries.ITEM, location)
            val tagItemIds = BuiltInRegistries.ITEM.getTagOrEmpty(tag)
                .mapTo(mutableSetOf()) { BuiltInRegistries.ITEM.getKey(it.value()).toString() }
            inventoryItemIds.filter { it in tagItemIds }
        }
    }

    private fun RecipeTreeP.processes(): List<RecipeProcess> {
        val processes = mutableListOf<RecipeProcess>()
        root?.collectProcesses(processes)
        return processes
    }

    private fun RecipeTreeNode.collectProcesses(processes: MutableList<RecipeProcess>) {
        process?.let { processes += it }
        children.forEach { it.collectProcesses(processes) }
    }

    private fun RecipeProcess.collectTagIds(tagIds: MutableSet<String>) {
        inputs.forEach { it.collectTagIds(tagIds) }
        catalysts.forEach { it.collectTagIds(tagIds) }
        renderOnly.forEach { it.collectTagIds(tagIds) }
        shape?.key?.values?.forEach { it.collectTagIds(tagIds) }
    }

    private fun RecipeIngredient.collectTagIds(tagIds: MutableSet<String>) {
        tags.forEach { tagIds += it.tagId }
    }

}
