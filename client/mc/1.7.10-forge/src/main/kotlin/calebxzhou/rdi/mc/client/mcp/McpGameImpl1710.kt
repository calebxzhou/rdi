package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.RDIClient
import calebxzhou.rdi.mc.common2.mcp.McpBadRequestError
import calebxzhou.rdi.mc.common2.mcp.McpBadSlotError
import calebxzhou.rdi.mc.common2.mcp.McpError
import calebxzhou.rdi.mc.common2.mcp.McpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFetchBoxQ
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindP
import calebxzhou.rdi.mc.common2.mcp.model.BlockFindQ
import calebxzhou.rdi.mc.common2.mcp.model.ContainerSlot
import calebxzhou.rdi.mc.common2.mcp.model.EntityPos
import calebxzhou.rdi.mc.common2.mcp.model.InventoryCompart
import calebxzhou.rdi.mc.common2.mcp.model.InventoryListP
import calebxzhou.rdi.mc.common2.mcp.model.InventorySlotQ
import calebxzhou.rdi.mc.common2.mcp.model.McpC2SNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.McpS2CNetPacket
import calebxzhou.rdi.mc.common2.mcp.model.ModInfo
import calebxzhou.rdi.mc.common2.mcp.model.PlayerInfo
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcess
import calebxzhou.rdi.mc.common2.mcp.model.RecipeProcessTextView
import calebxzhou.rdi.mc.common2.mcp.model.RecipeQ
import calebxzhou.rdi.mc.common2.mcp.model.RecipeTreeQ
import cpw.mods.fml.common.Loader
import cpw.mods.fml.common.ModContainer
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.Minecraft
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object McpGameImpl1710 : McpGameInterface {
    private const val RESPONSE_TIMEOUT_SECONDS = 30L
    private const val SCREENSHOT_TIMEOUT_SECONDS = 5L

    private val pendingPacketMap = ConcurrentHashMap<String, CompletableFuture<McpS2CNetPacket>>()
    private val minecraft get() = Minecraft.getMinecraft()

    override fun send(packet: McpC2SNetPacket): Result<String> {
        return runCatching {
            if (minecraft.thePlayer == null || minecraft.theWorld == null) {
                throw McpNoPlayerError()
            }
            val future = CompletableFuture<McpS2CNetPacket>()
            pendingPacketMap[packet.reqId] = future
            minecraft.func_152344_a {
                try {
                    McpClientNetwork1710.CHANNEL.sendToServer(GameNetPayload1710.fromC2S(packet))
                } catch (e: Throwable) {
                    pendingPacketMap.remove(packet.reqId)?.completeExceptionally(e)
                }
            }
            future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).text
        }.recoverCatching { e ->
            throw when (e) {
                is TimeoutException -> McpError("server time out")
                is ExecutionException -> McpError("server execution error")
                else -> McpError("server unavailable")
            }
        }.also {
            pendingPacketMap.remove(packet.reqId)
        }
    }

    override fun inventory(): Result<InventoryListP> = runCatching {
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        InventoryListP(
            inv = player.inventory.mainInventory.mapIndexed(::slot),
            armor = player.inventory.armorInventory.mapIndexed(::slot),
            offhand = emptyList(),
        )
    }

    override fun inventorySlot(req: InventorySlotQ): Result<String> = runCatching {
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        val stack = when (req.compart) {
            InventoryCompart.INV -> player.inventory.mainInventory.getOrNull(req.slotId)
            InventoryCompart.ARMOR -> player.inventory.armorInventory.getOrNull(req.slotId)
            InventoryCompart.OFFHAND -> throw McpBadSlotError()
        } ?: throw McpBadSlotError()
        val nbt = stack.writeToNBT(NBTTagCompound()).toString()
        "${stack.displayName}\n${nbt}"
    }

    override fun blockFind(req: BlockFindQ): Result<BlockFindP> = BlockMcpImpl1710.find(req)

    override fun blockFetchBox(req: BlockFetchBoxQ): Result<BlockFetchBoxP> = BlockMcpImpl1710.fetchBox(req)

    override fun playerInfo(): Result<PlayerInfo> = runCatching {
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        PlayerInfo(
            dim = "legacy:${player.dimension}",
            uuid = player.uniqueID.toString(),
            name = player.getCommandSenderName(),
            pos = EntityPos(player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch),
            health = player.health,
            maxHealth = player.getMaxHealth(),
            food = player.foodStats.getFoodLevel(),
            gameMode = "unknown",
        )
    }

    override fun recipes(req: RecipeQ): Result<String> = runCatching {
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
            throw McpNoPlayerError()
        }
        if (!NeiRecipeProcessCollector1710.isReady()) {
            return@runCatching "unresolved reason=nei_not_ready"
        }
        val recipesByItem = req.items.associateWith { itemId ->
            if (minecraft.func_152345_ab()) {
                NeiRecipeProcessCollector1710.collect(itemId)
            } else {
                val future = CompletableFuture<List<RecipeProcess>>()
                minecraft.func_152344_a {
                    try {
                        future.complete(NeiRecipeProcessCollector1710.collect(itemId))
                    } catch (e: Throwable) {
                        future.completeExceptionally(e)
                    }
                }
                future.get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        }
        RecipeProcessTextView.render(req.items, recipesByItem)
    }

    override fun recipeTree(req: RecipeTreeQ): Result<String> = unsupported()

    override fun screenshotPngData(): Result<ByteArray> = runCatching {
        if (minecraft.theWorld == null) {
            throw McpNoPlayerError()
        }
        if (minecraft.func_152345_ab()) {
            return@runCatching takeScreenshotPngData()
        }
        val future = CompletableFuture<ByteArray>()
        minecraft.func_152344_a {
            try {
                future.complete(takeScreenshotPngData())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        future.get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun modIds(): Result<String> = runCatching {
        Loader.instance().getActiveModList().joinToString(" ") { it.getModId() }
    }

    override fun modInfo(id: String): Result<ModInfo> = runCatching {
        val mod = Loader.instance().activeModList.firstOrNull { it.getModId() == id }
            ?: throw McpBadRequestError("unknown mod $id")
        ModInfo(
            id = mod.getModId(),
            name = mod.getName(),
            version = mod.getVersion(),
            description = modDescription(mod),
            dependencies = emptyList(),
        )
    }

    override fun questChapterList(): Result<String> = runCatching {
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        BetterQuestingMcpBridge1710.questChapterList(player).joinToString("\n") { chapter ->
            buildString {
                append(chapter.id)
                append(" title=").append(chapter.title)
                append(" group=").append(chapter.groupId).append(":").append(chapter.groupTitle)
                append(" completed=").append(chapter.completed)
                append(" quests=").append(chapter.questCount)
                append(" completedQuests=").append(chapter.completedQuestCount)
            }
        }.ifEmpty { "none" }
    }

    override fun questsOfChapter(chapterId: String): Result<String> = runCatching {
        val player = minecraft.thePlayer ?: throw McpNoPlayerError()
        val quests = BetterQuestingMcpBridge1710.questsOfChapter(chapterId, player)
        if (quests.isEmpty()) {
            return@runCatching "none"
        }
        quests.joinToString("\n") { quest ->
            val dependencyIds = quest.dependencies.joinToString(",") { it.id }.ifEmpty { "none" }
            "${quest.id} title=${quest.title} completed=${quest.state.completed} optional=${quest.rules.optional} repeatable=${quest.rules.repeatable} tasks=${quest.tasks.size} rewards=${quest.rewards.size} deps=$dependencyIds"
        }
    }

    override fun gameVersion(): String {
        return "1.7.10"
    }

    private fun slot(id: Int, stack: ItemStack?): ContainerSlot {
        return stack?.takeIf { it.stackSize > 0 }?.let {
            ContainerSlot(id, itemId(it), it.stackSize)
        } ?: ContainerSlot.empty(id)
    }

    private fun itemId(stack: ItemStack): String {
        val id = Item.itemRegistry.getNameForObject(stack.item)?.toString()
        return id ?: "unknown:${Item.getIdFromItem(stack.item)}"
    }

    private fun modDescription(mod: ModContainer): String {
        return runCatching { mod.getMetadata()?.description.orEmpty() }
            .getOrElse {
                RDIClient.LOG?.warn("Failed to read mod metadata for ${mod.getModId()}", it)
                ""
            }
    }

    private fun takeScreenshotPngData(): ByteArray {
        val framebuffer = minecraft.framebuffer
        var width = minecraft.displayWidth
        var height = minecraft.displayHeight
        if (OpenGlHelper.isFramebufferEnabled()) {
            width = framebuffer.framebufferTextureWidth
            height = framebuffer.framebufferTextureHeight
        }

        val pixelCount = width * height
        val pixelBuffer = BufferUtils.createIntBuffer(pixelCount)
        val pixelValues = IntArray(pixelCount)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
        pixelBuffer.clear()

        if (OpenGlHelper.isFramebufferEnabled()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture)
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer)
        } else {
            GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer)
        }

        pixelBuffer.get(pixelValues)
        TextureUtil.func_147953_a(pixelValues, width, height)
        val image = if (OpenGlHelper.isFramebufferEnabled()) {
            val cropped = BufferedImage(framebuffer.framebufferWidth, framebuffer.framebufferHeight, BufferedImage.TYPE_INT_RGB)
            val yOffset = framebuffer.framebufferTextureHeight - framebuffer.framebufferHeight
            for (y in yOffset until framebuffer.framebufferTextureHeight) {
                for (x in 0 until framebuffer.framebufferWidth) {
                    cropped.setRGB(x, y - yOffset, pixelValues[y * framebuffer.framebufferTextureWidth + x])
                }
            }
            cropped
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also {
                it.setRGB(0, 0, width, height, pixelValues, 0, width)
            }
        }
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }
    }

    fun complete(packet: McpS2CNetPacket) {
        pendingPacketMap.remove(packet.reqId)?.complete(packet)
    }

    private fun <T> unsupported(): Result<T> =
        Result.failure(McpError("unsupported in mc1.7.10"))
}
