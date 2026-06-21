package calebxzhou.rdi.mc.client.mcpimpl

import calebxzau.mc.common2021.mc
import calebxzau.mc.common2021.resId
import calebxzhou.rdi.mc.client.mcp.McpGameInterface
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.McpBadSlotError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.model.*
import com.mojang.blaze3d.pipeline.RenderCall
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Screenshot
import net.minecraft.world.item.ItemStack
import net.minecraftforge.fml.ModList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object McpGameImpl : McpGameInterface {
    private const val RESPONSE_TIMEOUT_SECONDS = 30L

    private val pendingPacketMap = ConcurrentHashMap<String, CompletableFuture<McpS2CNetPacket>>()

    override fun send(packet: McpC2SNetPacket): Result<String> {
        return runCatching {
            val future = CompletableFuture<McpS2CNetPacket>()
            pendingPacketMap[packet.reqId] = future
            mc.execute {
                try {
                    McpNetwork.sendToServer(packet)
                } catch (e: Throwable) {
                    pendingPacketMap.remove(packet.reqId)?.completeExceptionally(e)
                }
            }
            future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).text
        }.recoverCatching { e ->
            throw when (e) {
                is TimeoutException -> McpError("server time out")
                is ExecutionException -> McpError(e.cause?.message ?: "server execution error")
                else -> McpError(e.message ?: "server unavailable")
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
        val tag = stack.tag?.toString() ?: "{}"
        "${stack}\n$tag"
    }

    override fun blockFind(req: BlockFindQ): Result<BlockFindP> = BlockMcpImpl.find(req)

    override fun blockFetchBox(req: BlockFetchBoxQ): Result<BlockFetchBoxP> = BlockMcpImpl.fetchBox(req)

    override fun playerInfo(): Result<PlayerInfo> = runCatching {
        val player = mc.player ?: throw McpNoPlayerError()
        PlayerInfo(
            dim = player.level().dimension().location().toString(),
            uuid = player.uuid.toString(),
            name = player.name.string,
            pos = EntityPos(player.x, player.y, player.z, player.yRot, player.xRot),
            health = player.health,
            maxHealth = player.maxHealth,
            food = player.foodData.foodLevel,
            gameMode = mc.gameMode?.playerMode?.getName() ?: "unknown",
        )
    }

    override fun recipes(req: RecipeQ): Result<String> = runCatching {
        "unsupported reason=not_implemented_1_20"
    }

    override fun recipeTree(req: RecipeTreeQ): Result<String> = runCatching {
        "unsupported reason=not_implemented_1_20"
    }

    override fun screenshotPngData(): Result<ByteArray> = runCatching {
        val future = CompletableFuture<ByteArray>()
        val capture = RenderCall {
            try {
                val image = Screenshot.takeScreenshot(mc.mainRenderTarget)
                try {
                    future.complete(image.asByteArray())
                } catch (e: Throwable) {
                    future.completeExceptionally(e)
                } finally {
                    image.close()
                }
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        if (RenderSystem.isOnRenderThread()) {
            capture.execute()
        } else {
            RenderSystem.recordRenderCall(capture)
        }
        future.get(5, TimeUnit.SECONDS)
    }.recoverCatching { e ->
        if (e is InterruptedException) {
            Thread.currentThread().interrupt()
        }
        throw when (e) {
            is TimeoutException -> McpError("screenshot timeout")
            is InterruptedException -> McpError("screenshot interrupted")
            else -> e
        }
    }

    override fun modIds(): Result<String> = runCatching {
        ModList.get().mods.joinToString(" ") { it.modId }
    }

    override fun modInfo(id: String): Result<ModInfo> = runCatching {
        val mod = ModList.get().getModContainerById(id).orElseThrow {
            McpBadRequestError("unknown mod $id")
        }.modInfo
        ModInfo(
            id = mod.modId,
            name = mod.displayName,
            version = mod.version.toString(),
            description = mod.description,
            dependencies = mod.dependencies.map { dep ->
                ModDependency(
                    id = dep.modId,
                    versionRange = dep.versionRange.toString(),
                    type = dep.type.name.lowercase(),
                    ordering = dep.ordering.name.lowercase(),
                    side = dep.side.name.lowercase(),
                )
            },
        )
    }

    override fun questChapterList(): Result<String> = runCatching {
        "unsupported reason=not_implemented_1_20"
    }

    override fun questsOfChapter(chapterId: String): Result<String> = runCatching {
        "unsupported reason=not_implemented_1_20"
    }

    override fun gameVersion(): String = "1.20.1"

    fun complete(packet: McpS2CNetPacket) {
        pendingPacketMap.remove(packet.reqId)?.complete(packet)
    }

    private fun slot(id: Int, stack: ItemStack): ContainerSlot {
        return if (stack.isEmpty) {
            ContainerSlot.empty(id)
        } else {
            ContainerSlot(id, stack.item.resId.toString(), stack.count)
        }
    }
}
