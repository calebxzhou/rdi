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
import net.minecraft.core.registries.BuiltInRegistries
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

}
