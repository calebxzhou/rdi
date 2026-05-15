package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.mcp.RMcpMcDataCodec211
import calebxzhou.rdi.mc.client.mcp.RMcp211
import calebxzhou.rdi.mc.client.rcmd.RcmdClientCommands
import calebxzhou.rdi.mc.common.RDI
import calebxzhou.rdi.mc.common2.mcp.RErrorCode
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockMapData
import calebxzhou.rdi.mc.common2.mcp.RBlockPos
import calebxzhou.rdi.mc.common2.mcp.RMcpBlocksFindData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlocksFindRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpEndpointException
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryMoveData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventorySwapData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryTagMatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemSearchData
import calebxzhou.rdi.mc.common2.mcp.RMcpLangKeyIndex
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyEntitiesData
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyResourcesData
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerData
import calebxzhou.rdi.mc.common2.mcp.REntityPosData
import calebxzhou.rdi.mc.common2.mcp.RMcpResolveSearchData
import calebxzhou.rdi.mc.common2.mcp.RMcpSectionSemanticData
import calebxzhou.rdi.mc.common2.mcp.RMcpSituationData
import com.google.common.net.HostAndPort
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.language.ClientLanguage
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.SectionPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientChatEvent
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.Locale
import java.util.PriorityQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Supplier
import kotlin.jvm.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * calebxzhou @ 2026-01-10 22:33
 */
@Mod("rdi")
@EventBusSubscriber(modid = "rdi", value = [Dist.CLIENT])
class RDIMain {

    init {
        RMcp211.start()
    }

    @JvmRecord
    private data class LangKeyIndexCache(val resourceManager: Any?, val index: RMcpLangKeyIndex?)

    @JvmRecord
    private data class ItemSearchIndexCache(val resourceManager: Any?, val index: ItemSearchIndex)

    @JvmRecord
    private data class ResolveSearchIndexCache(val resourceManager: Any?, val index: ResolveSearchIndex)

    private fun interface ResolveSearchEntryFactory {
        fun entries(
            english: MutableMap<String, String>,
            chinese: MutableMap<String, String>
        ): MutableList<ResolveSearchEntry>
    }

    @JvmRecord
    private data class ResolveSearchEntry(
        val id: String?,
        val namespace: String?,
        val langkey: String?,
        val englishName: String?,
        val chineseName: String?,
        val modId: String?,
        val modName: String?,
        val normalizedId: String?,
        val normalizedPath: String?,
        val normalizedLangkey: String?,
        val normalizedEnglishName: String?,
        val normalizedChineseName: String?,
        val normalizedModName: String?
    )

    @JvmRecord
    private data class ResolveSearchCandidate(val entry: ResolveSearchEntry, val score: Double, val match: String?)

    private class ResolveSearchIndex(private val kind: String?, private val entries: MutableList<ResolveSearchEntry>) {
        fun search(text: String?, modId: String?, limit: Int): RMcpResolveSearchData {
            val page = searchEntries(
                text,
                modId,
                limit,
                entries,
                ::score,
                { it.score },
                { it.entry },
                { it.namespace },
                { it.id }
            ) { candidate, entry ->
                RMcpResolveSearchData.Result(
                    entry.id,
                    entry.namespace,
                    entry.langkey,
                    entry.englishName,
                    entry.chineseName,
                    entry.modId,
                    entry.modName,
                    candidate.score,
                    candidate.match
                )
            }
            return RMcpResolveSearchData(
                kind,
                text,
                page.modId,
                limit,
                page.results
            )
        }

        companion object {
            private fun score(entry: ResolveSearchEntry, query: String, modId: String): ResolveSearchCandidate {
                val scored = scoreSearchFields(
                    query,
                    entry.namespace,
                    modId,
                    entry.normalizedChineseName,
                    entry.normalizedEnglishName,
                    entry.normalizedId,
                    "id",
                    entry.normalizedPath,
                    entry.normalizedLangkey,
                    entry.normalizedModName
                )
                return ResolveSearchCandidate(entry, scored.score, scored.match)
            }
        }
    }

    @JvmRecord
    private data class ItemSearchEntry(
        val itemId: String?,
        val namespace: String?,
        val langkey: String?,
        val englishName: String?,
        val chineseName: String?,
        val modId: String?,
        val modName: String?,
        val normalizedItemId: String?,
        val normalizedPath: String?,
        val normalizedLangkey: String?,
        val normalizedEnglishName: String?,
        val normalizedChineseName: String?,
        val normalizedModName: String?
    )

    @JvmRecord
    private data class ItemSearchCandidate(val entry: ItemSearchEntry, val score: Double, val match: String?)

    private class ItemSearchIndex(private val entries: MutableList<ItemSearchEntry>) {
        fun search(text: String?, modId: String?, limit: Int): RMcpItemSearchData {
            val page = searchEntries(
                text,
                modId,
                limit,
                entries,
                ::score,
                { it.score },
                { it.entry },
                { it.namespace },
                { it.itemId }
            ) { candidate, entry ->
                RMcpItemSearchData.Result(
                    entry.itemId,
                    entry.namespace,
                    entry.langkey,
                    entry.englishName,
                    entry.chineseName,
                    entry.modId,
                    entry.modName,
                    candidate.score,
                    candidate.match
                )
            }
            return RMcpItemSearchData(
                text,
                page.modId,
                limit,
                page.results
            )
        }

        companion object {
            private fun score(entry: ItemSearchEntry, query: String, modId: String): ItemSearchCandidate {
                val scored = scoreSearchFields(
                    query,
                    entry.namespace,
                    modId,
                    entry.normalizedChineseName,
                    entry.normalizedEnglishName,
                    entry.normalizedItemId,
                    "item_id",
                    entry.normalizedPath,
                    entry.normalizedLangkey,
                    entry.normalizedModName
                )
                return ItemSearchCandidate(entry, scored.score, scored.match)
            }
        }
    }

    @JvmRecord
    private data class SearchResultPage<R>(val modId: String?, val results: MutableList<R>)

    @JvmRecord
    private data class ScoredMatch(val score: Double, val match: String?)

    @JvmRecord
    private data class InventorySlotRef(val section: String?, val index: Int, val canonical: String?, val menuSlot: Int)

    private class ResourceAccumulator(private val id: String?, private val category: String?) {
        private val sections = LinkedHashMap<String, SectionResourceCount>()
        private var count = 0
        private var nearest: RBlockPos? = null
        private var nearestDistanceSquared = Long.MAX_VALUE

        fun id(): String? {
            return id
        }

        fun category(): String? {
            return category
        }

        fun count(): Int {
            return count
        }

        fun add(
            chunkX: Int,
            chunkZ: Int,
            sectionY: Int,
            blockX: Int,
            blockY: Int,
            blockZ: Int,
            centerX: Int,
            centerY: Int,
            centerZ: Int
        ) {
            count++
            val key = "$chunkX,$chunkZ,$sectionY"
            sections.getOrPut(key) {
                SectionResourceCount(
                    chunkX,
                    chunkZ,
                    sectionY
                )
            }.add()
            val dx = (blockX - centerX).toLong()
            val dy = (blockY - centerY).toLong()
            val dz = (blockZ - centerZ).toLong()
            val distanceSquared = dx * dx + dy * dy + dz * dz
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared
                nearest = RBlockPos(blockX, blockY, blockZ)
            }
        }

        fun data(): RMcpNearbyResourcesData.Resource {
            val sectionData = sections.values
                .sortedWith(
                    compareByDescending<SectionResourceCount> { it.count() }
                        .thenBy { it.chunkX() }
                        .thenBy { it.chunkZ() }
                        .thenBy { it.sectionY() }
                )
                .take(12)
                .mapTo(ArrayList<RMcpNearbyResourcesData.ResourceSection>()) { it.data() }
            return RMcpNearbyResourcesData.Resource(id, category, count, nearest, sectionData)
        }
    }

    private class SectionResourceCount(private val chunkX: Int, private val chunkZ: Int, private val sectionY: Int) {
        private var count = 0

        fun chunkX(): Int {
            return chunkX
        }

        fun chunkZ(): Int {
            return chunkZ
        }

        fun sectionY(): Int {
            return sectionY
        }

        fun count(): Int {
            return count
        }

        fun add() {
            count++
        }

        fun data(): RMcpNearbyResourcesData.ResourceSection {
            return RMcpNearbyResourcesData.ResourceSection(chunkX, chunkZ, sectionY, count)
        }
    }

    @JvmRecord
    private data class SectionAnalysis(
        val summary: RMcpSectionSemanticData.Summary,
        val layers: MutableList<RMcpSectionSemanticData.Layer>?,
        val legend: MutableMap<String, String>?,
        val features: RMcpSectionSemanticData.Features?,
        val counts: MutableMap<String, Int>?,
        val minNonAirY: Int?,
        val maxNonAirY: Int?
    )

    @JvmRecord
    private data class BlockFindHit(
        val id: String?,
        val pos: RBlockPos?,
        val state: String?,
        val distanceSquared: Long
    )

    companion object {

        private fun <E, C, R> searchEntries(
            text: String?,
            modId: String?,
            limit: Int,
            entries: MutableList<E>,
            score: (E, String, String) -> C,
            candidateScore: (C) -> Double,
            candidateEntry: (C) -> E,
            entryNamespace: (E) -> String?,
            entryId: (E) -> String?,
            result: (C, E) -> R
        ): SearchResultPage<R> {
            val query = normalizeSearchText(text)
            val normalizedModId = normalizeSearchText(modId)
            if (query.isEmpty()) {
                return SearchResultPage(modId, ArrayList())
            }

            val candidates = ArrayList<C>()
            for (entry in entries) {
                val candidate = score(entry, query, normalizedModId)
                if (candidateScore(candidate) > 0.0) {
                    candidates.add(candidate)
                }
            }
            candidates.sortWith(
                compareByDescending<C> { candidateScore(it) }
                    .thenBy { if (entryNamespace(candidateEntry(it)) == "minecraft") 1 else 0 }
                    .thenBy { entryId(candidateEntry(it)) }
            )

            val results = ArrayList<R>()
            for (candidate in candidates) {
                if (results.size >= limit) {
                    break
                }
                results.add(result(candidate, candidateEntry(candidate)))
            }
            return SearchResultPage(if (normalizedModId.isEmpty()) null else modId, results)
        }

        private fun scoreSearchFields(
            query: String,
            namespace: String?,
            modId: String,
            chineseName: String?,
            englishName: String?,
            id: String?,
            idField: String,
            path: String?,
            langkey: String?,
            modName: String?
        ): ScoredMatch {
            var bestScore = 0.0
            var match: String? = null
            var scored = scoreSearchField(query, chineseName.orEmpty(), "chinese")
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            scored = scoreSearchField(query, englishName.orEmpty(), "english")
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            scored = scoreSearchField(query, id.orEmpty(), idField)
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            scored = scoreSearchField(query, path.orEmpty(), "path")
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            scored = scoreSearchField(query, langkey.orEmpty(), "langkey")
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            scored = scoreSearchField(query, modName.orEmpty(), "mod_name")
            if (scored.score > bestScore) {
                bestScore = scored.score
                match = scored.match
            }
            if (bestScore <= 0.0) {
                return ScoredMatch(0.0, "")
            }
            val score = when {
                modId.isNotEmpty() && namespace == modId -> bestScore + 2.0
                modId.isNotEmpty() -> bestScore - 0.5
                namespace != "minecraft" -> bestScore + 0.05
                else -> bestScore
            }
            return ScoredMatch(score, match)
        }

        private fun scoreSearchField(query: String, candidate: String, field: String): ScoredMatch {
            if (candidate.isEmpty()) {
                return ScoredMatch(0.0, "")
            }
            if (candidate == query) {
                return ScoredMatch(4.0, "exact_$field")
            }
            if (candidate.startsWith(query)) {
                return ScoredMatch(3.0, "prefix_$field")
            }
            if (candidate.contains(query)) {
                return ScoredMatch(2.0, "contains_$field")
            }
            val fuzzy = fuzzySearchScore(query, candidate)
            return if (fuzzy <= 0.0) ScoredMatch(0.0, "") else ScoredMatch(fuzzy, "fuzzy_$field")
        }

        private const val GRID_SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val SCREENSHOT_EXECUTOR: ExecutorService =
            Executors.newSingleThreadExecutor(ThreadFactory { task: Runnable? ->
                val thread = Thread(task, "rdi-mcp-screenshot.md")
                thread.setDaemon(true)
                thread
            })

        @Volatile
        private var langKeyIndex: LangKeyIndexCache? = null

        @Volatile
        private var itemSearchIndex: ItemSearchIndexCache? = null

        @Volatile
        private var blockSearchIndex: ResolveSearchIndexCache? = null

        @Volatile
        private var entityTypeSearchIndex: ResolveSearchIndexCache? = null

        @Volatile
        private var fluidSearchIndex: ResolveSearchIndexCache? = null

        @Volatile
        private var tagSearchIndex: ResolveSearchIndexCache? = null

        @JvmField
        var JOIN_BUTTON: Button =
            Button.builder(Component.literal("进入地图 · " + RDI.HOST_NAME), Button.OnPress { _ ->
                val hp = HostAndPort.fromString(RDI.GAME_IP)
                ConnectScreen.startConnecting(
                    TitleScreen(),
                    Minecraft.getInstance(),
                    ServerAddress(hp.getHost(), hp.getPort()),
                    ServerData("rdi", RDI.GAME_IP, ServerData.Type.OTHER),
                    false,
                    null
                )
            }).bounds(100, 0, 200, 50).build()

        @JvmStatic
        fun layoutJoinButton(screenWidth: Int) {
            JOIN_BUTTON.setX(screenWidth / 2 - 100)
            JOIN_BUTTON.setY(0)
            JOIN_BUTTON.setWidth(200)
            JOIN_BUTTON.setHeight(20)
        }

        @SubscribeEvent
        fun onRegisterClientCommands(event: RegisterClientCommandsEvent) {
            event.getDispatcher().register(
                Commands.literal("rdi")
                    .then(
                        Commands.literal("firmchunk")
                            .then(
                                Commands.literal("show")
                                    .executes { context ->
                                        RDI.SHOW_FIRM_CHUNKS = true
                                        context.source.sendSuccess({ Component.literal("永久区块边框：显示") }, false)
                                        1
                                    }
                            )
                            .then(
                                Commands.literal("hide")
                                    .executes { context ->
                                        RDI.SHOW_FIRM_CHUNKS = false
                                        context.source.sendSuccess({ Component.literal("永久区块边框：隐藏") }, false)
                                        1
                                    }
                            )
                    )
            )
        }

        @SubscribeEvent
        fun onClientChat(event: ClientChatEvent) {
            val message = event.getMessage()
            if (!RcmdClientCommands.isRcmd(message)) {
                return
            }
            val minecraft = Minecraft.getInstance()
            val result = RcmdClientCommands.dispatch(minecraft, message)
            if (!result.found) {
                return
            }
            event.setCanceled(true)
            RcmdClientCommands.reply(minecraft, result.result)
        }

        @SubscribeEvent
        fun onRenderLevelStage(event: RenderLevelStageEvent) {
            if (event.getStage() !== RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || !RDI.SHOW_FIRM_CHUNKS) {
                return
            }
            val cameraEntity = event.getCamera().getEntity()
            if (cameraEntity == null) {
                return
            }
            val sectionPos = SectionPos.of(cameraEntity)
            val cameraPos = event.getCamera().getPosition()
            val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
            val renderType = RenderType.debugLineStrip(4.0)
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val matrix4f = event.getPoseStack().last().pose()
            val minX = (sectionPos.minBlockX() - cameraPos.x).toFloat()
            val minY = (sectionPos.minBlockY() - cameraPos.y).toFloat()
            val minZ = (sectionPos.minBlockZ() - cameraPos.z).toFloat()
            val maxX = minX + 16.0f
            val maxY = minY + 16.0f
            val maxZ = minZ + 16.0f

            addLine(vertexConsumer, matrix4f, minX, minY, minZ, maxX, minY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, minY, maxZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, minX, minY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, minY, minZ)

            addLine(vertexConsumer, matrix4f, minX, maxY, minZ, maxX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, maxY, minZ, maxX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, maxX, maxY, maxZ, minX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, maxY, maxZ, minX, maxY, minZ)

            addLine(vertexConsumer, matrix4f, minX, minY, minZ, minX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, minZ, maxX, maxY, minZ)
            addLine(vertexConsumer, matrix4f, maxX, minY, maxZ, maxX, maxY, maxZ)
            addLine(vertexConsumer, matrix4f, minX, minY, maxZ, minX, maxY, maxZ)

            bufferSource.endBatch(renderType)
        }

        private fun addLine(
            vertexConsumer: VertexConsumer,
            matrix4f: Matrix4f,
            x1: Float,
            y1: Float,
            z1: Float,
            x2: Float,
            y2: Float,
            z2: Float
        ) {
            vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0f, 1.0f, 0.0f, 0.0f)
            vertexConsumer.addVertex(matrix4f, x1, y1, z1).setColor(1.0f, 1.0f, 0.0f, 1.0f)
            vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0f, 1.0f, 0.0f, 1.0f)
            vertexConsumer.addVertex(matrix4f, x2, y2, z2).setColor(1.0f, 1.0f, 0.0f, 0.0f)
        }

        fun playerBrief(minecraft: Minecraft, player: Player): RMcpPlayerData {
            val dim = player.level().dimension().location().toString()
            val playerInfo = if (minecraft.getConnection() == null) null else minecraft.getConnection()!!
                .getPlayerInfo(player.getUUID())
            return RMcpPlayerData(
                dim,
                player.getStringUUID(),
                player.getName().getString(),
                REntityPosData(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()),
                player.getHealth(),
                player.getMaxHealth(),
                player.getFoodData().getFoodLevel(),
                if (playerInfo == null || playerInfo.getGameMode() == null) null else playerInfo.getGameMode()
                    .getName(),
                if (playerInfo == null) null else playerInfo.getLatency()
            )
        }

        fun inventoryData(minecraft: Minecraft): RMcpInventoryData? {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null) {
                return null
            }
            val inventory = player.getInventory()
            val registryAccess = level.registryAccess()
            val dim = level.dimension().location().toString()
            val hotbar: List<RMcpInventoryData.Item> =
                compactItemRange("hotbar", inventory.items, 0, 9, registryAccess)
            val items: List<RMcpInventoryData.Item> =
                compactItemRange("inventory", inventory.items, 9, inventory.items.size, registryAccess)
            val armor: List<RMcpInventoryData.Item> =
                compactItemRange("armor", inventory.armor, 0, inventory.armor.size, registryAccess)
            val offhand: List<RMcpInventoryData.Item> =
                compactItemRange("offhand", inventory.offhand, 0, inventory.offhand.size, registryAccess)
            return RMcpInventoryData(
                dim,
                inventory.selected,
                compactItem("hotbar", inventory.selected, inventory.selected, inventory.getSelected(), registryAccess),
                hotbar,
                items,
                armor,
                offhand,
                inventorySummary(allInventoryStacks(player))
            )
        }

        fun swapInventorySlots(
            minecraft: Minecraft,
            fromText: String?,
            toText: String?,
            dryRun: Boolean
        ): RMcpInventorySwapData? {
            try {
                return minecraft.submit<RMcpInventorySwapData?>(Supplier {
                    swapInventorySlotsOnClient(
                        minecraft,
                        fromText,
                        toText,
                        dryRun
                    )
                }).get(2, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is RMcpEndpointException) throw cause
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            } catch (e: TimeoutException) {
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            }
        }

        fun moveInventoryItems(
            minecraft: Minecraft,
            fromText: String?,
            toText: String?,
            count: Int,
            dryRun: Boolean
        ): RMcpInventoryMoveData? {
            try {
                return minecraft.submit<RMcpInventoryMoveData?>(Supplier {
                    moveInventoryItemsOnClient(
                        minecraft,
                        fromText,
                        toText,
                        count,
                        dryRun
                    )
                }).get(2, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            } catch (e: ExecutionException) {
                val cause = e.cause
                if (cause is RMcpEndpointException) throw cause
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            } catch (e: TimeoutException) {
                throw RMcpEndpointException(RErrorCode.ACTION_FAILED)
            }
        }

        private fun swapInventorySlotsOnClient(
            minecraft: Minecraft,
            fromText: String?,
            toText: String?,
            dryRun: Boolean
        ): RMcpInventorySwapData {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null || minecraft.gameMode == null) {
                throw RMcpEndpointException(RErrorCode.NO_PLAYER)
            }
            if (player.containerMenu !== player.inventoryMenu) {
                throw RMcpEndpointException(RErrorCode.BUSY_CONTAINER_OPEN)
            }
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                throw RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY)
            }
            val from: InventorySlotRef = parseInventorySlot(fromText)
            val to: InventorySlotRef = parseInventorySlot(toText)
            if (from.canonical == to.canonical) {
                throw RMcpEndpointException(RErrorCode.SAME_SLOT)
            }
            val registryAccess = level.registryAccess()
            val fromStack: ItemStack = stackAtSlot(player, from)
            val toStack: ItemStack = stackAtSlot(player, to)
            val beforeFrom: RMcpInventoryData.Item? = itemAtSlot(player, from, registryAccess)
            val beforeTo: RMcpInventoryData.Item? = itemAtSlot(player, to, registryAccess)
            if (!dryRun && !fromStack.isEmpty() && !toStack.isEmpty() && fromStack.isStackable() && toStack.isStackable() && ItemStack.isSameItemSameComponents(
                    fromStack,
                    toStack
                )
            ) {
                throw RMcpEndpointException(RErrorCode.UNSUPPORTED_MERGE_RISK)
            }
            if (!dryRun) {
                val menu = player.inventoryMenu
                minecraft.gameMode!!.handleInventoryMouseClick(
                    menu.containerId,
                    from.menuSlot,
                    0,
                    ClickType.PICKUP,
                    player
                )
                minecraft.gameMode!!.handleInventoryMouseClick(
                    menu.containerId,
                    to.menuSlot,
                    0,
                    ClickType.PICKUP,
                    player
                )
                minecraft.gameMode!!.handleInventoryMouseClick(
                    menu.containerId,
                    from.menuSlot,
                    0,
                    ClickType.PICKUP,
                    player
                )
            }
            val afterFrom: RMcpInventoryData.Item? = itemAtSlot(player, from, registryAccess)
            val afterTo: RMcpInventoryData.Item? = itemAtSlot(player, to, registryAccess)
            return RMcpInventorySwapData(
                from.canonical,
                to.canonical,
                dryRun,
                !itemsEqual(beforeFrom, afterFrom) || !itemsEqual(beforeTo, afterTo),
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo,
                inventoryData(minecraft)
            )
        }

        private fun moveInventoryItemsOnClient(
            minecraft: Minecraft,
            fromText: String?,
            toText: String?,
            count: Int,
            dryRun: Boolean
        ): RMcpInventoryMoveData? {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null || minecraft.gameMode == null) {
                return null
            }
            if (player.containerMenu !== player.inventoryMenu) {
                throw RMcpEndpointException(RErrorCode.BUSY_CONTAINER_OPEN)
            }
            if (!player.inventoryMenu.getCarried().isEmpty()) {
                throw RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY)
            }
            if (count <= 0) {
                throw RMcpEndpointException(RErrorCode.BAD_COUNT)
            }
            val from: InventorySlotRef = parseInventorySlot(fromText)
            val to: InventorySlotRef = parseInventorySlot(toText)
            if (from.canonical == to.canonical) {
                throw RMcpEndpointException(RErrorCode.SAME_SLOT)
            }
            val registryAccess = level.registryAccess()
            val fromStack: ItemStack = stackAtSlot(player, from)
            val toStack: ItemStack = stackAtSlot(player, to)
            val beforeFrom: RMcpInventoryData.Item? = itemAtSlot(player, from, registryAccess)
            val beforeTo: RMcpInventoryData.Item? = itemAtSlot(player, to, registryAccess)
            if (fromStack.isEmpty()) {
                throw RMcpEndpointException(RErrorCode.EMPTY_SOURCE)
            }
            if (count > fromStack.getCount()) {
                throw RMcpEndpointException(RErrorCode.BAD_COUNT)
            }
            if (!toStack.isEmpty() && !ItemStack.isSameItemSameComponents(fromStack, toStack)) {
                throw RMcpEndpointException(RErrorCode.INCOMPATIBLE_TARGET)
            }
            val sourceSlot = player.inventoryMenu.getSlot(from.menuSlot)
            if (!sourceSlot.mayPickup(player)) {
                throw RMcpEndpointException(RErrorCode.EMPTY_SOURCE)
            }
            val targetSlot = player.inventoryMenu.getSlot(to.menuSlot)
            if (!targetSlot.mayPlace(fromStack)) {
                throw RMcpEndpointException(RErrorCode.INCOMPATIBLE_TARGET)
            }
            val targetCount = if (toStack.isEmpty()) 0 else toStack.getCount()
            val targetCapacity = targetSlot.getMaxStackSize(fromStack) - targetCount
            if (count > targetCapacity) {
                throw RMcpEndpointException(RErrorCode.TARGET_FULL)
            }
            if (!dryRun) {
                clickInventorySlot(minecraft, player, from, 0)
                if (count == fromStack.getCount()) {
                    clickInventorySlot(minecraft, player, to, 0)
                } else {
                    for (i in 0..<count) {
                        clickInventorySlot(minecraft, player, to, 1)
                    }
                    clickInventorySlot(minecraft, player, from, 0)
                }
                if (!player.inventoryMenu.getCarried().isEmpty()) {
                    clickInventorySlot(minecraft, player, from, 0)
                }
                if (!player.inventoryMenu.getCarried().isEmpty()) {
                    throw RMcpEndpointException(RErrorCode.CARRIED_ITEM_NOT_EMPTY)
                }
            }
            val afterFrom: RMcpInventoryData.Item? = itemAtSlot(player, from, registryAccess)
            val afterTo: RMcpInventoryData.Item? = itemAtSlot(player, to, registryAccess)
            return RMcpInventoryMoveData(
                from.canonical,
                to.canonical,
                count,
                dryRun,
                !itemsEqual(beforeFrom, afterFrom) || !itemsEqual(beforeTo, afterTo),
                if (dryRun) 0 else count,
                beforeFrom,
                beforeTo,
                afterFrom,
                afterTo,
                inventoryData(minecraft)
            )
        }

        private fun clickInventorySlot(minecraft: Minecraft, player: Player, slot: InventorySlotRef, button: Int) {
            minecraft.gameMode!!.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                slot.menuSlot,
                button,
                ClickType.PICKUP,
                player
            )
        }

        fun situationData(
            minecraft: Minecraft,
            entityRadius: Double,
            resourceChunkRadius: Int,
            resourceSectionRadius: Int
        ): RMcpSituationData? {

        }

        private fun environmentData(level: ClientLevel, player: Player): RMcpSituationData.Environment {
            val pos = player.blockPosition()
            val timeOfDay = Math.floorMod(level.getDayTime(), 24000L)
            return RMcpSituationData.Environment(
                level.dimension().location().toString(),
                biomeId(level, pos),
                level.getGameTime(),
                level.getDayTime(),
                timeOfDay,
                timeBucket(timeOfDay),
                level.isRaining(),
                level.isThundering(),
                level.getDifficulty().getKey(),
                RMcpSituationData.Light(
                    level.getBrightness(LightLayer.BLOCK, pos),
                    level.getBrightness(LightLayer.SKY, pos),
                    level.getMaxLocalRawBrightness(pos)
                ),
                level.canSeeSky(pos),
                player.isInWater(),
                player.isUnderWater(),
                player.onGround()
            )
        }

        private fun biomeId(level: ClientLevel, pos: BlockPos): String {
            return level.getBiome(pos)
                .unwrapKey()
                .map { it.location().toString() }
                .orElse("unknown")
        }

        private fun timeBucket(timeOfDay: Long): String {
            if (timeOfDay < 1000 || timeOfDay >= 23000) {
                return "dawn"
            }
            if (timeOfDay < 12000) {
                return "day"
            }
            if (timeOfDay < 13800) {
                return "dusk"
            }
            return "night"
        }

        private fun compactItemRange(
            section: String?,
            stacks: MutableList<ItemStack>,
            from: Int,
            to: Int,
            registryAccess: HolderLookup.Provider
        ): List<RMcpInventoryData.Item> {
            val items = ArrayList<RMcpInventoryData.Item>()
            for (slot in from..<to) {
                val item: RMcpInventoryData.Item? = compactItem(
                    section,
                    slot,
                    if ("hotbar" == section) slot else null,
                    stacks.get(slot),
                    registryAccess
                )
                if (item != null) {
                    items.add(item)
                }
            }
            return items
        }

        private fun compactItem(
            section: String?,
            slot: Int,
            hotbarSlot: Int?,
            stack: ItemStack?,
            registryAccess: HolderLookup.Provider
        ): RMcpInventoryData.Item? {
            if (stack == null || stack.isEmpty()) {
                return null
            }
            return RMcpInventoryData.Item(
                section,
                slot,
                hotbarSlot,
                itemId(stack),
                stack.getCount(),
                stack.saveOptional(registryAccess).toString()
            )
        }

        fun inventoryTagMatchData(
            minecraft: Minecraft,
            tagText: String?,
            scopeText: String?,
            limit: Int
        ): RMcpInventoryTagMatchData? {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null) {
                return null
            }
            val tag = itemTagKey(tagText)
            val scope: String = normalizeInventoryTagScope(scopeText)
            val registryAccess = level.registryAccess()
            val matches = ArrayList<RMcpInventoryTagMatchData.Match>()
            var totalCount = 0
            val inventory = player.getInventory()

            if (tagScopeIncludes(scope, "hotbar")) {
                totalCount += addInventoryTagMatches(
                    player,
                    matches,
                    tag,
                    "hotbar",
                    inventory.items,
                    0,
                    9,
                    registryAccess,
                    limit
                )
            }
            if (tagScopeIncludes(scope, "main")) {
                totalCount += addInventoryTagMatches(
                    player,
                    matches,
                    tag,
                    "main",
                    inventory.items,
                    9,
                    inventory.items.size,
                    registryAccess,
                    limit
                )
            }
            if (tagScopeIncludes(scope, "armor")) {
                totalCount += addInventoryTagMatches(
                    player,
                    matches,
                    tag,
                    "armor",
                    inventory.armor,
                    0,
                    inventory.armor.size,
                    registryAccess,
                    limit
                )
            }
            if (tagScopeIncludes(scope, "offhand")) {
                totalCount += addInventoryTagMatches(
                    player,
                    matches,
                    tag,
                    "offhand",
                    inventory.offhand,
                    0,
                    inventory.offhand.size,
                    registryAccess,
                    limit
                )
            }
            if (tagScopeIncludes(scope, "container") && player.containerMenu !== player.inventoryMenu) {
                totalCount += addContainerTagMatches(player, matches, tag, registryAccess, limit)
            }

            return RMcpInventoryTagMatchData(
                tag.location().toString(),
                scope,
                player.containerMenu !== player.inventoryMenu,
                totalCount,
                matches.size,
                matches
            )
        }

        private fun itemTagKey(tagText: String?): TagKey<Item> {
            var text = if (tagText == null) "" else tagText.trim { it <= ' ' }
            if (text.startsWith("#")) {
                text = text.substring(1)
            }
            val id = ResourceLocation.tryParse(text)
            if (id == null) {
                throw RMcpEndpointException(RErrorCode.BAD_ITEM_ID)
            }
            return TagKey.create(BuiltInRegistries.ITEM.key(), id)
        }

        private fun normalizeInventoryTagScope(scopeText: String?): String {
            val scope =
                if (scopeText == null || scopeText.isBlank()) "all" else scopeText.trim { it <= ' ' }.lowercase()
            return when (scope) {
                "all", "player", "hotbar", "main", "inventory", "armor", "offhand", "container" -> scope
                else -> throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
        }

        private fun tagScopeIncludes(scope: String, area: String?): Boolean {
            if ("all" == scope) {
                return true
            }
            if ("player" == scope) {
                return "container" != area
            }
            return scope == area || ("inventory" == scope && ("hotbar" == area || "main" == area))
        }

        private fun addInventoryTagMatches(
            player: Player,
            matches: MutableList<RMcpInventoryTagMatchData.Match>,
            tag: TagKey<Item>,
            area: String,
            stacks: MutableList<ItemStack>,
            from: Int,
            to: Int,
            registryAccess: HolderLookup.Provider,
            limit: Int
        ): Int {
            var totalCount = 0
            for (slot in from..<to) {
                val stack = stacks.get(slot)
                if (!stack.isEmpty() && stack.`is`(tag)) {
                    totalCount += stack.getCount()
                    if (matches.size < limit) {
                        matches.add(
                            RMcpInventoryTagMatchData.Match(
                                "player",
                                area,
                                slot,
                                menuSlotForPlayerInventoryArea(player, area, slot),
                                compactItem(
                                    inventoryItemSection(area),
                                    slot,
                                    if ("hotbar" == area) slot else null,
                                    stack,
                                    registryAccess
                                )
                            )
                        )
                    }
                }
            }
            return totalCount
        }

        private fun inventoryItemSection(area: String?): String? {
            return if ("main" == area) "inventory" else area
        }

        private fun menuSlotForPlayerInventoryArea(player: Player, area: String, slot: Int): Int? {
            val inventory = player.getInventory()
            val inventorySlot = when (area) {
                "hotbar", "main" -> slot
                "armor" -> 36 + slot
                "offhand" -> 40
                else -> -1
            }
            if (inventorySlot < 0) {
                return null
            }
            for (menuSlot in player.containerMenu.slots.indices) {
                val current = player.containerMenu.slots.get(menuSlot)
                if (current.container === inventory && current.getSlotIndex() == inventorySlot) {
                    return menuSlot
                }
            }
            return null
        }

        private fun addContainerTagMatches(
            player: Player,
            matches: MutableList<RMcpInventoryTagMatchData.Match>,
            tag: TagKey<Item>,
            registryAccess: HolderLookup.Provider,
            limit: Int
        ): Int {
            var totalCount = 0
            for (menuSlot in player.containerMenu.slots.indices) {
                val slot = player.containerMenu.slots.get(menuSlot)
                if (slot.container === player.getInventory()) {
                    continue
                }
                val stack = slot.getItem()
                if (!stack.isEmpty() && stack.`is`(tag)) {
                    totalCount += stack.getCount()
                    if (matches.size < limit) {
                        matches.add(
                            RMcpInventoryTagMatchData.Match(
                                "container",
                                "container",
                                slot.getSlotIndex(),
                                menuSlot,
                                compactItem("container", slot.getSlotIndex(), null, stack, registryAccess)
                            )
                        )
                    }
                }
            }
            return totalCount
        }

        private fun parseInventorySlot(text: String?): InventorySlotRef {
            val parts: Array<String?> =
                if (text == null) arrayOfNulls<String>(0) else text.trim { it <= ' ' }.lowercase(
                    Locale.getDefault()
                ).split(":".toRegex(), limit = 2).toTypedArray()
            if (parts.size != 2 || parts[0]!!.isBlank()) {
                throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
            val slot: Int
            try {
                slot = parts[1]!!.trim { it <= ' ' }.toInt()
            } catch (e: NumberFormatException) {
                throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
            return when (parts[0]!!.trim { it <= ' ' }) {
                "inventory" -> inventorySlot(slot)
                "hotbar" -> {
                    if (slot < 0 || slot > 8) {
                        throw RMcpEndpointException(RErrorCode.BAD_SLOT)
                    }
                    inventorySlot(slot)
                }

                "main" -> {
                    if (slot < 0 || slot > 26) {
                        throw RMcpEndpointException(RErrorCode.BAD_SLOT)
                    }
                    inventorySlot(slot + 9)
                }

                "armor" -> armorSlot(slot)
                "offhand" -> offhandSlot(slot)
                else -> throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
        }

        private fun inventorySlot(slot: Int): InventorySlotRef {
            if (slot < 0 || slot > 35) {
                throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
            val menuSlot = if (slot < 9) slot + 36 else slot
            return InventorySlotRef("inventory", slot, "inventory:" + slot, menuSlot)
        }

        private fun armorSlot(slot: Int): InventorySlotRef {
            if (slot < 0 || slot > 3) {
                throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
            return InventorySlotRef("armor", slot, "armor:" + slot, 8 - slot)
        }

        private fun offhandSlot(slot: Int): InventorySlotRef {
            if (slot != 0) {
                throw RMcpEndpointException(RErrorCode.BAD_SLOT)
            }
            return InventorySlotRef("offhand", 0, "offhand:0", 45)
        }

        private fun itemAtSlot(
            player: Player,
            slot: InventorySlotRef,
            registryAccess: HolderLookup.Provider
        ): RMcpInventoryData.Item? {
            val stack = when (slot.section) {
                "inventory" -> player.getInventory().items.get(slot.index)
                "armor" -> player.getInventory().armor.get(slot.index)
                "offhand" -> player.getInventory().offhand.get(slot.index)
                else -> ItemStack.EMPTY
            }
            return compactItem(
                slot.section,
                slot.index,
                if (slot.index < 9 && "inventory" == slot.section) slot.index else null,
                stack,
                registryAccess
            )
        }

        private fun stackAtSlot(player: Player, slot: InventorySlotRef): ItemStack {
            return when (slot.section) {
                "inventory" -> player.getInventory().items.get(slot.index)
                "armor" -> player.getInventory().armor.get(slot.index)
                "offhand" -> player.getInventory().offhand.get(slot.index)
                else -> ItemStack.EMPTY
            }
        }

        private fun itemsEqual(left: RMcpInventoryData.Item?, right: RMcpInventoryData.Item?): Boolean {
            if (left == null || right == null) {
                return left === right
            }
            return left.id == right.id
                    && left.count == right.count && left.snbt == right.snbt
        }

        private fun inventorySummary(stacks: MutableList<ItemStack>): RMcpInventoryData.Summary {
            val counts = LinkedHashMap<String, Int>()
            var occupiedSlots = 0
            var totalItems = 0
            var hasFood = false
            var hasTool = false
            var hasWeapon = false
            var hasBlock = false
            for (stack in stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue
                }
                val id: String = itemId(stack)
                occupiedSlots++
                totalItems += stack.getCount()
                counts.merge(id, stack.getCount()) { a, b -> a + b }
                hasFood = hasFood || stack.get<FoodProperties?>(DataComponents.FOOD) != null
                hasTool = hasTool || isToolItem(id)
                hasWeapon = hasWeapon || isWeaponItem(id)
                hasBlock = hasBlock || stack.getItem() is BlockItem
            }
            val topItems = counts.entries
                .sortedWith { left, right ->
                    val countCompare = right.value!!.compareTo(left.value!!)
                    if (countCompare != 0) countCompare else left.key!!.compareTo(right.key!!)
                }
                .take(12)
                .mapTo(ArrayList<RMcpInventoryData.ItemCount>()) { entry ->
                    RMcpInventoryData.ItemCount(
                        entry.key,
                        entry.value!!
                    )
                }
            return RMcpInventoryData.Summary(
                occupiedSlots,
                stacks.size - occupiedSlots,
                totalItems,
                topItems,
                hasFood,
                hasTool,
                hasWeapon,
                hasBlock
            )
        }

        private fun allInventoryStacks(player: Player): MutableList<ItemStack> {
            val inventory = player.getInventory()
            val stacks = ArrayList<ItemStack>()
            stacks.addAll(inventory.items)
            stacks.addAll(inventory.armor)
            stacks.addAll(inventory.offhand)
            return stacks
        }

        private fun itemId(stack: ItemStack): String {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()
        }

        private fun isToolItem(id: String): Boolean {
            return id.endsWith("_pickaxe")
                    || id.endsWith("_axe")
                    || id.endsWith("_shovel")
                    || id.endsWith("_hoe")
                    || id.endsWith(":shears")
                    || id.endsWith(":flint_and_steel")
                    || id.endsWith(":bucket")
                    || id.endsWith("_bucket")
        }

        private fun isWeaponItem(id: String): Boolean {
            return id.endsWith("_sword")
                    || id.endsWith(":bow")
                    || id.endsWith(":crossbow")
                    || id.endsWith(":trident")
                    || id.endsWith(":mace")
        }

        fun nearbyEntitiesData(
            minecraft: Minecraft,
            x: Int,
            y: Int,
            z: Int,
            radius: Double,
            categories: MutableList<String>,
            limit: Int
        ): RMcpNearbyEntitiesData? {
            val level = minecraft.level
            if (level == null) {
                return null
            }
            val dim = level.dimension().location().toString()
            val center = Vec3(x + 0.5, y + 0.5, z + 0.5)
            val radiusSqr = radius * radius
            val entities = ArrayList<RMcpNearbyEntitiesData.Entity>()
            for (entity in level.entitiesForRendering()) {
                if (entity === minecraft.player || entity.isRemoved()) {
                    continue
                }
                if (entity is ItemEntity && entity.getItem().isEmpty()) {
                    continue
                }
                val category: String = entityCategory(entity)
                if (!matchesCategory(category, categories)) {
                    continue
                }
                val distanceSqr = entity.position().distanceToSqr(center)
                if (distanceSqr > radiusSqr) {
                    continue
                }
                entities.add(nearbyEntityData(dim, entity, category, sqrt(distanceSqr)))
            }
            val sortedEntities = entities.sortedWith(
                compareBy<RMcpNearbyEntitiesData.Entity> { it.distance }
                    .thenBy { it.type }
                    .thenBy { it.uuid }
            )
            val returnedEntities = sortedEntities
                .take(limit)
                .toCollection(ArrayList<RMcpNearbyEntitiesData.Entity>())

            var monsters = 0
            var animals = 0
            var items = 0
            var nearestMonster: RMcpNearbyEntitiesData.EntityRef? = null
            var nearestAnimal: RMcpNearbyEntitiesData.EntityRef? = null
            var nearestItem: RMcpNearbyEntitiesData.EntityRef? = null
            for (entity in sortedEntities) {
                if ("monster" == entity.category) {
                    monsters++
                    if (nearestMonster == null) {
                        nearestMonster = RMcpNearbyEntitiesData.EntityRef(entity.type, entity.distance)
                    }
                } else if ("animal" == entity.category) {
                    animals++
                    if (nearestAnimal == null) {
                        nearestAnimal = RMcpNearbyEntitiesData.EntityRef(entity.type, entity.distance)
                    }
                } else if ("item" == entity.category) {
                    items++
                    if (nearestItem == null) {
                        nearestItem = RMcpNearbyEntitiesData.EntityRef(entity.type, entity.distance)
                    }
                }
            }

            return RMcpNearbyEntitiesData(
                dim,
                RMcpNearbyEntitiesData.Center(
                    RBlockPos(x, y, z),
                    Math.floorDiv(x, 16),
                    Math.floorDiv(z, 16),
                    Math.floorDiv(y, 16)
                ),
                radius,
                RMcpNearbyEntitiesData.Summary(
                    sortedEntities.size,
                    monsters,
                    animals,
                    items,
                    nearestMonster,
                    nearestAnimal,
                    nearestItem
                ),
                returnedEntities
            )
        }

        private fun nearbyEntityData(
            dim: String?,
            entity: Entity,
            category: String?,
            distance: Double
        ): RMcpNearbyEntitiesData.Entity {
            val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
            var health: Float? = null
            var maxHealth: Float? = null
            var baby: Boolean? = null
            if (entity is LivingEntity) {
                health = entity.getHealth()
                maxHealth = entity.getMaxHealth()
                baby = entity.isBaby()
            }
            var item: RMcpNearbyEntitiesData.Item? = null
            if (entity is ItemEntity && !entity.getItem().isEmpty()) {
                item = RMcpNearbyEntitiesData.Item(entity.getItem().saveOptional(entity.registryAccess()).toString())
            }
            return RMcpNearbyEntitiesData.Entity(
                dim,
                entity.getStringUUID(),
                type,
                entity.getName().getString(),
                category,
                REntityPosData(dim, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()),
                distance,
                health,
                maxHealth,
                "monster" == category,
                baby,
                item
            )
        }

        private fun entityCategory(entity: Entity): String {
            if (entity is ItemEntity) {
                return "item"
            }
            val mobCategory = entity.getType().getCategory()
            if (mobCategory == MobCategory.MONSTER || entity is Monster) {
                return "monster"
            }
            if (entity is Animal
                || mobCategory == MobCategory.CREATURE || mobCategory == MobCategory.AMBIENT || mobCategory == MobCategory.AXOLOTLS || mobCategory == MobCategory.UNDERGROUND_WATER_CREATURE || mobCategory == MobCategory.WATER_CREATURE || mobCategory == MobCategory.WATER_AMBIENT
            ) {
                return "animal"
            }
            return mobCategory.getName()
        }

        private fun matchesCategory(category: String?, categories: MutableList<String>): Boolean {
            return categories.contains("all") || categories.contains(category)
        }

        fun blockMapSliceData(minecraft: Minecraft, x: Int?, y: Int?, z: Int?, radius: Int): RMcpBlockMapData? {
            val level = minecraft.level
            val center: BlockPos? = blockMapCenter(minecraft, x, y, z)
            if (level == null || center == null) {
                return null
            }
            if (level.isOutsideBuildHeight(center)) {
                throw RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE)
            }
            val loadedChunks: Int = ensureBlockMapChunksLoaded(level, center, radius)
            val counts = LinkedHashMap<String, Int>()
            val blockIds = arrayOfNulls<String>((radius * 2 + 1) * (radius * 2 + 1))
            var index = 0
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    val pos = BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz)
                    val id: String = blockId(level.getBlockState(pos))
                    blockIds[index++] = id
                    counts.merge(id, 1) { a, b -> a + b }
                }
            }
            val legend: MutableMap<String, String> = blockMapLegend(counts)
            val symbolById: MutableMap<String, String> = symbolById(legend)
            val rows = ArrayList<String>()
            val size = radius * 2 + 1
            index = 0
            for (dz in -radius..radius) {
                val line = StringBuilder(size)
                for (dx in -radius..radius) {
                    val symbol = symbolById.getOrDefault(blockIds[index++], "?")
                    line.append(
                        blockMapOverlaySymbol(
                            minecraft,
                            center.getX() + dx,
                            center.getY(),
                            center.getZ() + dz,
                            center,
                            symbol
                        )
                    )
                }
                rows.add(line.toString())
            }
            return RMcpBlockMapData(
                level.dimension().location().toString(),
                "slice",
                blockPosData(center),
                radius,
                blockMapAxes(),
                center.getY(),
                null,
                RMcpBlockMapData.Summary(size, size, loadedChunks, 0, 0, 0, 0, 0, topBlocks(counts, 16)),
                legend,
                rows,
                mutableListOf<RMcpBlockMapData.Cell>()
            )
        }

        private fun blockMapCenter(minecraft: Minecraft, x: Int?, y: Int?, z: Int?): BlockPos? {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null) {
                return null
            }
            if (x == null || y == null || z == null) {
                return player.blockPosition()
            }
            return BlockPos(x, y, z)
        }

        private fun blockMapAxes(): RMcpBlockMapData.Axes {
            return RMcpBlockMapData.Axes(
                "z increases downward",
                "x increases rightward",
                "top-left is center minus radius on x and z"
            )
        }

        private fun ensureBlockMapChunksLoaded(level: ClientLevel, center: BlockPos, radius: Int): Int {
            val minChunkX = Math.floorDiv(center.getX() - radius, 16)
            val maxChunkX = Math.floorDiv(center.getX() + radius, 16)
            val minChunkZ = Math.floorDiv(center.getZ() - radius, 16)
            val maxChunkZ = Math.floorDiv(center.getZ() + radius, 16)
            var loaded = 0
            for (chunkX in minChunkX..maxChunkX) {
                for (chunkZ in minChunkZ..maxChunkZ) {
                    if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
                        throw RMcpEndpointException(RErrorCode.CHUNK_NOT_LOADED)
                    }
                    loaded++
                }
            }
            return loaded
        }

        private fun blockMapLegend(counts: MutableMap<String, Int>): MutableMap<String, String> {
            val legend = LinkedHashMap<String, String>()
            legend.put(".", "minecraft:air")
            legend.put("P", "player xz")
            legend.put("C", "center xz")
            var symbolIndex = 0
            var hasOther = false
            for (block in topBlocks(counts, counts.size)) {
                if (block == null) {
                    continue
                }
                if ("minecraft:air" == block.id) {
                    continue
                }
                var symbol: String? = null
                while (symbolIndex < GRID_SYMBOLS.length) {
                    val candidate = GRID_SYMBOLS.get(symbolIndex++).toString()
                    if (!legend.containsKey(candidate)) {
                        symbol = candidate
                        break
                    }
                }
                if (symbol == null) {
                    hasOther = true
                    continue
                }
                legend.put(symbol, block.id)
            }
            if (hasOther) {
                legend.put("?", "other")
            }
            return legend
        }

        private fun symbolById(legend: MutableMap<String, String>): MutableMap<String, String> {
            val symbolById = LinkedHashMap<String, String>()
            for (entry in legend.entries) {
                if ("player xz" != entry.value && "center xz" != entry.value) {
                    symbolById.put(entry.value, entry.key)
                }
            }
            return symbolById
        }

        private fun blockMapOverlaySymbol(
            minecraft: Minecraft,
            x: Int,
            y: Int,
            z: Int,
            center: BlockPos,
            fallback: String?
        ): String? {
            val player = minecraft.player
            if (player != null && player.blockPosition().getX() == x && player.blockPosition().getZ() == z) {
                return "P"
            }
            if (center.getX() == x && center.getZ() == z) {
                return "C"
            }
            return fallback
        }

        fun blocksFindData(minecraft: Minecraft, request: RMcpBlocksFindRequest): RMcpBlocksFindData? {
            val level = minecraft.level
            val player = minecraft.player
            if (level == null || player == null) {
                return null
            }
            val targetIds = ArrayList<String>()
            for (id in request.ids!!) {
                val location = ResourceLocation.tryParse(id)
                if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
                    throw RMcpEndpointException(RErrorCode.BAD_BLOCK_IDS)
                }
                targetIds.add(location.toString())
            }

            val center = player.blockPosition()
            val centerChunkX = Math.floorDiv(center.getX(), 16)
            val centerChunkZ = Math.floorDiv(center.getZ(), 16)
            val centerSectionY = Math.floorDiv(center.getY(), 16)
            val scanMode =
                if (request.scanMode == null || request.scanMode!!.isBlank()) "nearby_sections" else request.scanMode!!.trim { it <= ' ' }
            val minSectionY: Int
            val maxSectionY: Int
            if ("chunk" == scanMode) {
                minSectionY = level.getMinSection()
                maxSectionY = level.getMaxSection() - 1
            } else {
                val sectionRadius: Int = (if (request.sectionRadius == null) 1 else request.sectionRadius)!!
                minSectionY = max(level.getMinSection(), centerSectionY - sectionRadius)
                maxSectionY = min(level.getMaxSection() - 1, centerSectionY + sectionRadius)
            }
            if (minSectionY > maxSectionY) {
                throw RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE)
            }

            val counts = LinkedHashMap<String, Int>()
            val hitOrder = compareBy<BlockFindHit> { it.distanceSquared }
                .thenBy { it.id }
                .thenBy { it.pos?.y ?: 0 }
                .thenBy { it.pos?.x ?: 0 }
                .thenBy { it.pos?.z ?: 0 }
            val nearestHits: PriorityQueue<BlockFindHit> = PriorityQueue<BlockFindHit>(hitOrder.reversed())
            val nearestById: LinkedHashMap<String, BlockFindHit> = LinkedHashMap<String, BlockFindHit>()
            var loadedChunks = 0
            var skippedChunks = 0
            var scannedSections = 0
            var scannedBlocks = 0
            var matched = 0
            val includeState = Boolean.TRUE == request.includeState

            for (chunkX in centerChunkX - request.chunkRadius!!..centerChunkX + request.chunkRadius!!) {
                for (chunkZ in centerChunkZ - request.chunkRadius!!..centerChunkZ + request.chunkRadius!!) {
                    val chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                    if (chunk == null) {
                        skippedChunks++
                        continue
                    }
                    loadedChunks++
                    for (sectionY in minSectionY..maxSectionY) {
                        val section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY))
                        scannedSections++
                        for (localY in 0..15) {
                            val blockY = (sectionY shl 4) + localY
                            for (localZ in 0..15) {
                                val blockZ = chunk.getPos().getMinBlockZ() + localZ
                                for (localX in 0..15) {
                                    val blockX = chunk.getPos().getMinBlockX() + localX
                                    scannedBlocks++
                                    val state = section.getBlockState(localX, localY, localZ)
                                    val blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                                    if (!targetIds.contains(blockId)) {
                                        continue
                                    }
                                    matched++
                                    counts.merge(blockId, 1) { a, b -> a + b }
                                    val pos = RBlockPos(blockX, blockY, blockZ)
                                    val stateText = if (includeState) RMcpMcDataCodec211.stateString(
                                        blockId,
                                        state.toString()
                                    ) else null
                                    val hit = BlockFindHit(
                                        blockId,
                                        pos,
                                        stateText,
                                        blockDistanceSquared(blockX, blockY, blockZ, center)
                                    )
                                    val nearest = nearestById.get(blockId)
                                    if (nearest == null || hitOrder.compare(hit, nearest) < 0) {
                                        nearestById.put(blockId, hit)
                                    }
                                    if (nearestHits.size < request.limit!!) {
                                        nearestHits.add(hit)
                                    } else if (hitOrder.compare(hit, nearestHits.peek()) < 0) {
                                        nearestHits.poll()
                                        nearestHits.add(hit)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val returnedHits: ArrayList<BlockFindHit> = ArrayList<BlockFindHit>(nearestHits)
            returnedHits.sort(hitOrder)
            val returned = returnedHits.size
            val matches = ArrayList<RMcpBlocksFindData.Match>()
            for (id in targetIds) {
                val count = counts.get(id)
                if (count == null) {
                    continue
                }
                val positions = ArrayList<RBlockPos>()
                val states = if (includeState) ArrayList<String>() else null
                for (hit in returnedHits) {
                    if (id != hit.id) {
                        continue
                    }
                    positions.add(hit.pos)
                    if (states != null) {
                        states.add(hit.state)
                    }
                }
                val nearest = nearestById.get(id)!!.pos
                matches.add(
                    RMcpBlocksFindData.Match(
                        id,
                        count,
                        nearest,
                        positions,
                        states
                    )
                )
            }

            return RMcpBlocksFindData(
                level.dimension().location().toString(),
                RMcpBlocksFindData.Center(blockPosData(center), centerChunkX, centerChunkZ, centerSectionY),
                RMcpBlocksFindData.Range(
                    request.chunkRadius!!,
                    request.sectionRadius,
                    scanMode,
                    minSectionY,
                    maxSectionY
                ),
                RMcpBlocksFindData.Scan(
                    loadedChunks,
                    skippedChunks,
                    scannedSections,
                    scannedBlocks,
                    matched,
                    returned,
                    matched > returned
                ),
                matches
            )
        }

        private fun blockDistanceSquared(x: Int, y: Int, z: Int, center: BlockPos): Long {
            val dx = (x - center.getX()).toLong()
            val dy = (y - center.getY()).toLong()
            val dz = (z - center.getZ()).toLong()
            return dx * dx + dy * dy + dz * dz
        }

        private fun blockId(state: BlockState): String {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
        }

        private fun blockPosData(pos: BlockPos): RBlockPos {
            return RBlockPos(pos.getX(), pos.getY(), pos.getZ())
        }

        fun nearbyResourcesData(
            minecraft: Minecraft,
            x: Int,
            y: Int,
            z: Int,
            chunkRadius: Int,
            sectionRadius: Int,
            categories: MutableList<String>? = mutableListOf<String>(),
            ids: MutableList<String>? = mutableListOf<String>(),
            limit: Int = 64
        ): RMcpNearbyResourcesData? {
            val level = minecraft.level
            if (level == null) {
                return null
            }
            val dim = level.dimension().location().toString()
            val centerChunkX = Math.floorDiv(x, 16)
            val centerChunkZ = Math.floorDiv(z, 16)
            val centerSectionY = Math.floorDiv(y, 16)
            val minSectionY = max(level.getMinSection(), centerSectionY - sectionRadius)
            val maxSectionY = min(level.getMaxSection() - 1, centerSectionY + sectionRadius)
            if (minSectionY > maxSectionY) {
                throw RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE)
            }
            val filterCategories = categories?.toMutableList() ?: mutableListOf()
            val filterIds = ids?.toMutableList() ?: mutableListOf()

            val resources = LinkedHashMap<String, ResourceAccumulator>()
            val topCounts = LinkedHashMap<String, Int>()
            val requestedChunks = (chunkRadius * 2 + 1) * (chunkRadius * 2 + 1)
            val requestedSectionLevels = sectionRadius * 2 + 1
            val scannedSectionLevels = maxSectionY - minSectionY + 1
            val requestedSections = requestedChunks * requestedSectionLevels
            val skippedOutOfWorldSections = requestedChunks * (requestedSectionLevels - scannedSectionLevels)
            var loadedChunks = 0
            var skippedChunks = 0
            var scannedSections = 0
            var scannedBlocks = 0
            var hasWater = false
            var hasLava = false
            var hasOre = false
            var hasWood = false
            var hasCrops = false
            var hasBlockEntities = false

            for (chunkX in centerChunkX - chunkRadius..centerChunkX + chunkRadius) {
                for (chunkZ in centerChunkZ - chunkRadius..centerChunkZ + chunkRadius) {
                    val chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                    if (chunk == null) {
                        skippedChunks++
                        continue
                    }
                    loadedChunks++
                    val blockEntities = chunk.getBlockEntities()
                    for (sectionY in minSectionY..maxSectionY) {
                        val section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY))
                        scannedSections++
                        for (localY in 0..15) {
                            val blockY = (sectionY shl 4) + localY
                            for (localZ in 0..15) {
                                val blockZ = chunk.getPos().getMinBlockZ() + localZ
                                for (localX in 0..15) {
                                    val blockX = chunk.getPos().getMinBlockX() + localX
                                    scannedBlocks++
                                    val state = section.getBlockState(localX, localY, localZ)
                                    val blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                                    topCounts.merge(blockId, 1) { a, b -> a + b }

                                    val fluidState = state.getFluidState()
                                    if (!fluidState.isEmpty()) {
                                        val fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString()
                                        val fluidCategory: String = fluidResourceCategory(fluidId)
                                        hasWater = hasWater || "water" == fluidCategory
                                        hasLava = hasLava || "lava" == fluidCategory
                                        addResource(
                                            resources,
                                            fluidId,
                                            fluidCategory,
                                            chunkX,
                                            chunkZ,
                                            sectionY,
                                            blockX,
                                            blockY,
                                            blockZ,
                                            x,
                                            y,
                                            z
                                        )
                                    }

                                    var category: String? = blockResourceCategory(blockId)
                                    if (!blockEntities.isEmpty() && blockEntities.containsKey(
                                            BlockPos(
                                                blockX,
                                                blockY,
                                                blockZ
                                            )
                                        )
                                    ) {
                                        hasBlockEntities = true
                                        category = if (category == null) "block_entity" else category
                                    }
                                    if (category == null) {
                                        continue
                                    }
                                    hasOre = hasOre || "ore" == category
                                    hasWood = hasWood || "wood" == category
                                    hasCrops = hasCrops || "crop" == category
                                    addResource(
                                        resources,
                                        blockId,
                                        category,
                                        chunkX,
                                        chunkZ,
                                        sectionY,
                                        blockX,
                                        blockY,
                                        blockZ,
                                        x,
                                        y,
                                        z
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val matchedResources = resources.values
                .filter { resource ->
                    nearbyResourceMatches(
                        resource,
                        filterCategories,
                        filterIds
                    )
                }
                .sortedWith(
                    compareByDescending<ResourceAccumulator> { it.count() }
                        .thenBy { it.id() }
                )
            val resourceList = matchedResources
                .take(limit)
                .mapTo(ArrayList<RMcpNearbyResourcesData.Resource>()) { it.data() }
            val skippedReasons = ArrayList<RMcpNearbyResourcesData.SkippedReason>()
            if (skippedChunks > 0) {
                skippedReasons.add(RMcpNearbyResourcesData.SkippedReason("chunk_not_loaded", skippedChunks))
            }
            if (skippedOutOfWorldSections > 0) {
                skippedReasons.add(
                    RMcpNearbyResourcesData.SkippedReason(
                        "section_out_of_world",
                        skippedOutOfWorldSections
                    )
                )
            }
            return RMcpNearbyResourcesData(
                dim,
                RMcpNearbyResourcesData.Center(
                    RBlockPos(x, y, z),
                    centerChunkX,
                    centerChunkZ,
                    centerSectionY
                ),
                RMcpNearbyResourcesData.Range(chunkRadius, sectionRadius),
                RMcpNearbyResourcesData.Filter(filterCategories, filterIds, limit),
                RMcpNearbyResourcesData.Scan(
                    chunkRadius,
                    sectionRadius,
                    requestedChunks,
                    loadedChunks,
                    skippedChunks,
                    requestedSections,
                    scannedSections,
                    scannedBlocks,
                    matchedResources.size,
                    resourceList.size,
                    matchedResources.size > resourceList.size,
                    skippedReasons
                ),
                resourceList,
                topBlocks(topCounts, 24),
                RMcpNearbyResourcesData.Features(hasWater, hasLava, hasOre, hasWood, hasCrops, hasBlockEntities)
            )
        }

        private fun nearbyResourceMatches(
            resource: ResourceAccumulator,
            categories: MutableList<String>,
            ids: MutableList<String>
        ): Boolean {
            if (!ids.isEmpty() && !ids.contains(resource.id())) {
                return false
            }
            if (categories.isEmpty()) {
                return true
            }
            for (category in categories) {
                if (category == null) {
                    continue
                }
                if (nearbyResourceCategoryMatches(category, resource.category())) {
                    return true
                }
            }
            return false
        }

        private fun nearbyResourceCategoryMatches(filter: String, actual: String?): Boolean {
            if ("fluid" == filter) {
                return "fluid" == actual || "water" == actual || "lava" == actual
            }
            return filter == actual
        }

        private fun fluidResourceCategory(fluidId: String): String {
            if ("minecraft:water" == fluidId || fluidId.endsWith(":flowing_water")) {
                return "water"
            }
            if ("minecraft:lava" == fluidId || fluidId.endsWith(":flowing_lava")) {
                return "lava"
            }
            return "fluid"
        }

        private fun blockResourceCategory(id: String): String? {
            if (id.contains("_ore") || id.endsWith(":ancient_debris")) {
                return "ore"
            }
            if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae") || id.endsWith(
                    "_leaves"
                )
            ) {
                return "wood"
            }
            if (id.contains("crop") || id.endsWith(":wheat") || id.endsWith(":carrots") || id.endsWith(":potatoes") || id.endsWith(
                    ":beetroots"
                ) || id.endsWith(":melon") || id.endsWith(":pumpkin")
            ) {
                return "crop"
            }
            if (id.contains("chest") || id.endsWith(":barrel") || id.contains("shulker_box")) {
                return "container"
            }
            if (id.endsWith(":spawner")) {
                return "spawner"
            }
            return null
        }

        private fun addResource(
            resources: MutableMap<String, ResourceAccumulator>,
            id: String?,
            category: String?,
            chunkX: Int,
            chunkZ: Int,
            sectionY: Int,
            blockX: Int,
            blockY: Int,
            blockZ: Int,
            centerX: Int,
            centerY: Int,
            centerZ: Int
        ) {
            resources.getOrPut(id) { ResourceAccumulator(id, category) }
                .add(chunkX, chunkZ, sectionY, blockX, blockY, blockZ, centerX, centerY, centerZ)
        }

        private fun loadedChunk(level: ClientLevel, chunkX: Int, chunkZ: Int): LevelChunk {
            return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                ?: throw RMcpEndpointException(RErrorCode.CHUNK_NOT_LOADED)
        }

        private fun analyzeSection(chunk: LevelChunk, sectionY: Int, includeLayers: Boolean): SectionAnalysis {
            val level: Level = chunk.getLevel()!!
            if (sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) {
                throw RMcpEndpointException(RErrorCode.SECTION_OUT_OF_RANGE)
            }
            val section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY))
            val ids = if (includeLayers) arrayOfNulls<String>(4096) else null
            val nonAirMask = BooleanArray(4096)
            val counts = LinkedHashMap<String, Int>()
            val sectionPalette = LinkedHashMap<String, Boolean>()
            var hasFluids = false
            var nonAir = 0
            var minX = 16
            var minY = 16
            var minZ = 16
            var maxX = -1
            var maxY = -1
            var maxZ = -1
            for (localY in 0..15) {
                for (localZ in 0..15) {
                    for (localX in 0..15) {
                        val index: Int = sectionIndex(localX, localY, localZ)
                        val state = section.getBlockState(localX, localY, localZ)
                        val blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
                        counts.merge(blockId, 1) { a, b -> a + b }
                        sectionPalette.put(blockId, Boolean.TRUE)
                        if (ids != null) {
                            ids[index] = blockId
                        }
                        if (!state.getFluidState().isEmpty()) {
                            hasFluids = true
                        }
                        if (!state.isAir()) {
                            nonAir++
                            nonAirMask[index] = true
                            minX = min(minX, localX)
                            minY = min(minY, localY)
                            minZ = min(minZ, localZ)
                            maxX = max(maxX, localX)
                            maxY = max(maxY, localY)
                            maxZ = max(maxZ, localZ)
                        }
                    }
                }
            }
            val topBlocks = topBlocks(counts, 16)
            val bbox = if (nonAir == 0) null else RMcpSectionSemanticData.NonAirBox(
                RBlockPos(
                    chunk.getPos().getMinBlockX() + minX,
                    (sectionY shl 4) + minY,
                    chunk.getPos().getMinBlockZ() + minZ
                ),
                RBlockPos(
                    chunk.getPos().getMinBlockX() + maxX,
                    (sectionY shl 4) + maxY,
                    chunk.getPos().getMinBlockZ() + maxZ
                )
            )
            val summary = RMcpSectionSemanticData.Summary(
                nonAir == 0,
                nonAir,
                sectionPalette.size,
                topBlocks,
                bbox
            )
            val features = RMcpSectionSemanticData.Features(
                hasFluids,
                hasBlockEntitiesInSection(chunk, sectionY),
                countRegions(nonAirMask, true),
                countRegions(nonAirMask, false)
            )
            val legend = if (includeLayers) legend(counts) else mutableMapOf<String?, String?>()
            val layers =
                if (includeLayers) layers(ids!!, legend) else mutableListOf<RMcpSectionSemanticData.Layer>()
            return SectionAnalysis(
                summary,
                layers,
                legend,
                features,
                LinkedHashMap(counts),
                if (nonAir == 0) null else (sectionY shl 4) + minY,
                if (nonAir == 0) null else (sectionY shl 4) + maxY
            )
        }

        private fun blockYRange(sectionY: Int): RMcpSectionSemanticData.BlockYRange {
            val min = sectionY shl 4
            return RMcpSectionSemanticData.BlockYRange(min, min + 15)
        }

        private fun sectionIndex(localX: Int, localY: Int, localZ: Int): Int {
            return (localY shl 8) or (localZ shl 4) or localX
        }

        private fun topBlocks(
            counts: MutableMap<String, Int>,
            limit: Int
        ): MutableList<RMcpSectionSemanticData.BlockCount> {
            return counts.entries
                .sortedWith(
                    compareByDescending<MutableMap.MutableEntry<String?, Int?>> { it.value ?: 0 }
                        .thenBy { it.key }
                )
                .take(limit)
                .mapTo(ArrayList<RMcpSectionSemanticData.BlockCount>()) { entry ->
                    RMcpSectionSemanticData.BlockCount(
                        entry.key,
                        entry.value ?: 0
                    )
                }
        }

        private fun legend(counts: MutableMap<String, Int>): MutableMap<String, String> {
            val legend = LinkedHashMap<String, String>()
            legend.put(".", "minecraft:air")
            var symbolIndex = 0
            var hasOther = false
            for (block in topBlocks(counts, counts.size)) {
                if (block == null) {
                    continue
                }
                if ("minecraft:air" == block.id) {
                    continue
                }
                if (symbolIndex >= GRID_SYMBOLS.length) {
                    hasOther = true
                    continue
                }
                legend.put(GRID_SYMBOLS.get(symbolIndex++).toString(), block.id)
            }
            if (hasOther) {
                legend.put("?", "other")
            }
            return legend
        }

        private fun layers(
            ids: Array<String?>,
            legend: MutableMap<String, String>
        ): MutableList<RMcpSectionSemanticData.Layer> {
            val symbolById = LinkedHashMap<String, String>()
            for (entry in legend.entries) {
                symbolById.put(entry.value, entry.key)
            }
            val layers = ArrayList<RMcpSectionSemanticData.Layer>()
            for (localY in 0..15) {
                val grid = ArrayList<String>()
                val layerCounts = LinkedHashMap<String, Int>()
                for (localZ in 0..15) {
                    val line = StringBuilder(16)
                    for (localX in 0..15) {
                        val id = ids[sectionIndex(localX, localY, localZ)]
                        layerCounts.merge(id, 1) { a, b -> a + b }
                        line.append(symbolById.getOrDefault(id, "?"))
                    }
                    grid.add(line.toString())
                }
                layers.add(RMcpSectionSemanticData.Layer(localY, grid, topBlocks(layerCounts, 6)))
            }
            return layers
        }

        private fun hasBlockEntitiesInSection(chunk: LevelChunk, sectionY: Int): Boolean {
            val minY = sectionY shl 4
            val maxY = minY + 15
            for (pos in chunk.getBlockEntities().keys) {
                if (pos.getY() >= minY && pos.getY() <= maxY) {
                    return true
                }
            }
            return false
        }

        private fun countRegions(nonAirMask: BooleanArray, target: Boolean): Int {
            val visited = BooleanArray(4096)
            var regions = 0
            for (index in nonAirMask.indices) {
                if (visited[index] || nonAirMask[index] != target) {
                    continue
                }
                regions++
                floodRegion(nonAirMask, visited, index, target)
            }
            return regions
        }

        private fun floodRegion(nonAirMask: BooleanArray, visited: BooleanArray, start: Int, target: Boolean) {
            val queue = ArrayDeque<Int?>()
            queue.add(start)
            visited[start] = true
            while (!queue.isEmpty()) {
                val index: Int = queue.removeFirst()
                val localX = index and 15
                val localZ = (index shr 4) and 15
                val localY = (index shr 8) and 15
                addRegionNeighbor(nonAirMask, visited, queue, localX - 1, localY, localZ, target)
                addRegionNeighbor(nonAirMask, visited, queue, localX + 1, localY, localZ, target)
                addRegionNeighbor(nonAirMask, visited, queue, localX, localY - 1, localZ, target)
                addRegionNeighbor(nonAirMask, visited, queue, localX, localY + 1, localZ, target)
                addRegionNeighbor(nonAirMask, visited, queue, localX, localY, localZ - 1, target)
                addRegionNeighbor(nonAirMask, visited, queue, localX, localY, localZ + 1, target)
            }
        }

        private fun addRegionNeighbor(
            nonAirMask: BooleanArray,
            visited: BooleanArray,
            queue: ArrayDeque<Int?>,
            localX: Int,
            localY: Int,
            localZ: Int,
            target: Boolean
        ) {
            if (localX < 0 || localX > 15 || localY < 0 || localY > 15 || localZ < 0 || localZ > 15) {
                return
            }
            val index: Int = sectionIndex(localX, localY, localZ)
            if (!visited[index] && nonAirMask[index] == target) {
                visited[index] = true
                queue.add(index)
            }
        }

        fun langKeyIndex(minecraft: Minecraft): RMcpLangKeyIndex? {
            return cachedIndex(
                minecraft,
                { langKeyIndex },
                { it.resourceManager },
                { it.index },
                { langKeyIndex = it },
                { english, chinese -> RMcpLangKeyIndex.create(english, chinese) },
                { resourceManager, index -> LangKeyIndexCache(resourceManager, index) }
            )
        }

        fun itemSearchData(
            minecraft: Minecraft,
            text: String?,
            modId: String?,
            limit: Int
        ): RMcpItemSearchData {
            val index: ItemSearchIndex = itemSearchIndex(minecraft)
            return index.search(text, modId, limit)
        }

        fun blockSearchData(
            minecraft: Minecraft,
            text: String?,
            modId: String?,
            limit: Int
        ): RMcpResolveSearchData {
            return resolveSearchIndex(
                minecraft,
                "block",
                { blockSearchIndex },
                { blockSearchIndex = it },
                ResolveSearchEntryFactory { english, chinese ->
                    val entries = ArrayList<ResolveSearchEntry>()
                    for (block in BuiltInRegistries.BLOCK) {
                        val id: ResourceLocation = BuiltInRegistries.BLOCK.getKey(block)
                        if (id != null) {
                            entries.add(
                                resolveEntry(
                                    id.toString(),
                                    block.getDescriptionId(),
                                    english,
                                    chinese
                                )
                            )
                        }
                    }
                    entries
                }
            ).search(text, modId, limit)
        }

        fun entityTypeSearchData(
            minecraft: Minecraft,
            text: String?,
            modId: String?,
            limit: Int
        ): RMcpResolveSearchData {
            return resolveSearchIndex(
                minecraft,
                "entity_type",
                { entityTypeSearchIndex },
                { entityTypeSearchIndex = it },
                ResolveSearchEntryFactory { english, chinese ->
                    val entries = ArrayList<ResolveSearchEntry>()
                    for (entityType in BuiltInRegistries.ENTITY_TYPE) {
                        val id: ResourceLocation = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
                        entries.add(
                            resolveEntry(
                                id.toString(),
                                entityType.getDescriptionId(),
                                english,
                                chinese
                            )
                        )
                    }
                    entries
                }
            ).search(text, modId, limit)
        }

        fun fluidSearchData(
            minecraft: Minecraft,
            text: String?,
            modId: String?,
            limit: Int
        ): RMcpResolveSearchData {
            return resolveSearchIndex(
                minecraft,
                "fluid",
                { fluidSearchIndex },
                { fluidSearchIndex = it },
                { english, chinese ->
                    val entries = ArrayList<ResolveSearchEntry>()
                    for (fluid in BuiltInRegistries.FLUID) {
                        val id: ResourceLocation = BuiltInRegistries.FLUID.getKey(fluid)
                        entries.add(
                            resolveEntry(
                                id.toString(),
                                "fluid." + id.getNamespace() + "." + id.getPath(),
                                english,
                                chinese
                            )
                        )
                    }
                    entries
                }
            ).search(text, modId, limit)
        }

        fun tagSearchData(
            minecraft: Minecraft,
            text: String?,
            modId: String?,
            limit: Int
        ): RMcpResolveSearchData {
            return resolveSearchIndex(
                minecraft,
                "tag",
                { tagSearchIndex },
                { tagSearchIndex = it },
                { _, _ ->
                    val entries = ArrayList<ResolveSearchEntry>()
                    BuiltInRegistries.ITEM.getTagNames().forEach { tag ->
                        entries.add(
                            resolveTagEntry(
                                "item",
                                tag.location().toString()
                            )
                        )
                    }
                    BuiltInRegistries.BLOCK.getTagNames()
                        .forEach { tag ->
                            entries.add(
                                resolveTagEntry(
                                    "block",
                                    tag.location().toString()
                                )
                            )
                        }
                    BuiltInRegistries.FLUID.getTagNames()
                        .forEach { tag ->
                            entries.add(
                                resolveTagEntry(
                                    "fluid",
                                    tag.location().toString()
                                )
                            )
                        }
                    BuiltInRegistries.ENTITY_TYPE.getTagNames().forEach { tag ->
                        entries.add(
                            resolveTagEntry(
                                "entity_type",
                                tag.location().toString()
                            )
                        )
                    }
                    entries
                }
            ).search(text, modId, limit)
        }

        private fun resolveSearchIndex(
            minecraft: Minecraft,
            kind: String?,
            currentCache: () -> ResolveSearchIndexCache?,
            cacheUpdater: (ResolveSearchIndexCache) -> Unit,
            entryFactory: ResolveSearchEntryFactory
        ): ResolveSearchIndex {
            return cachedIndex(
                minecraft,
                currentCache,
                { it.resourceManager },
                { it.index },
                cacheUpdater,
                { english, chinese -> ResolveSearchIndex(kind, entryFactory.entries(english, chinese)) },
                { resourceManager, index -> ResolveSearchIndexCache(resourceManager, index) }
            )
        }

        private fun resolveEntry(
            id: String,
            langkey: String?,
            english: MutableMap<String, String>,
            chinese: MutableMap<String, String>
        ): ResolveSearchEntry {
            val namespace = id.substring(0, id.indexOf(':'))
            val modName = ModList.get().getModContainerById(namespace)
                .map<String> { it.getModInfo().getDisplayName() }
                .orElse(namespace)
            val englishName = if (langkey == null) "" else english.getOrDefault(langkey, "")
            val chineseName = if (langkey == null) "" else chinese.getOrDefault(langkey, "")
            return ResolveSearchEntry(
                id,
                namespace,
                langkey,
                englishName,
                chineseName,
                namespace,
                modName,
                normalizeSearchText(id),
                normalizeSearchText(id.substring(id.indexOf(':') + 1)),
                normalizeSearchText(langkey),
                normalizeSearchText(englishName),
                normalizeSearchText(chineseName),
                normalizeSearchText(modName)
            )
        }

        private fun resolveTagEntry(registry: String?, id: String): ResolveSearchEntry {
            val namespace = id.substring(0, id.indexOf(':'))
            val modName = ModList.get().getModContainerById(namespace)
                .map<String> { it.getModInfo().getDisplayName() }
                .orElse(namespace)
            val fullId = registry + "#" + id
            return ResolveSearchEntry(
                fullId,
                namespace,
                null,
                "",
                "",
                namespace,
                modName,
                normalizeSearchText(fullId),
                normalizeSearchText(id.substring(id.indexOf(':') + 1)),
                "",
                "",
                "",
                normalizeSearchText(modName)
            )
        }

        private fun itemSearchIndex(minecraft: Minecraft): ItemSearchIndex {
            return cachedIndex(
                minecraft,
                { itemSearchIndex },
                { it.resourceManager },
                { it.index },
                { itemSearchIndex = it },
                { english, chinese ->
                    val entries = ArrayList<ItemSearchEntry>()
                    for (item in BuiltInRegistries.ITEM) {
                        val id = BuiltInRegistries.ITEM.getKey(item)
                        if (id == null || "minecraft:air" == id.toString()) {
                            continue
                        }
                        val stack = ItemStack(item)
                        val langkey = item.getDescriptionId(stack)
                        val namespace = id.getNamespace()
                        val modName = ModList.get().getModContainerById(namespace)
                            .map<String> { it.getModInfo().getDisplayName() }
                            .orElse(namespace)
                        val englishName = english.getOrDefault(langkey, "")
                        val chineseName = chinese.getOrDefault(langkey, "")
                        entries.add(
                            ItemSearchEntry(
                                id.toString(),
                                namespace,
                                langkey,
                                englishName,
                                chineseName,
                                namespace,
                                modName,
                                normalizeSearchText(id.toString()),
                                normalizeSearchText(id.getPath()),
                                normalizeSearchText(langkey),
                                normalizeSearchText(englishName),
                                normalizeSearchText(chineseName),
                                normalizeSearchText(modName)
                            )
                        )
                    }
                    ItemSearchIndex(entries)
                },
                { resourceManager, index -> ItemSearchIndexCache(resourceManager, index) }
            )
        }

        private inline fun <C, I> cachedIndex(
            minecraft: Minecraft,
            crossinline currentCache: () -> C?,
            crossinline cacheResourceManager: (C) -> Any?,
            crossinline cacheIndex: (C) -> I,
            crossinline cacheUpdater: (C) -> Unit,
            crossinline buildIndex: (MutableMap<String, String>, MutableMap<String, String>) -> I,
            crossinline cacheFactory: (Any?, I) -> C
        ): I {
            val resourceManager = minecraft.getResourceManager()
            currentCache()?.takeIf { cacheResourceManager(it) === resourceManager }?.let { return cacheIndex(it) }

            synchronized(RDIMain::class.java) {
                currentCache()?.takeIf { cacheResourceManager(it) === resourceManager }?.let { return cacheIndex(it) }

                val english =
                    ClientLanguage.loadFrom(resourceManager, mutableListOf("en_us"), false).getLanguageData()
                val chinese =
                    ClientLanguage.loadFrom(resourceManager, mutableListOf("zh_cn"), false).getLanguageData()
                return buildIndex(english, chinese).also { cacheUpdater(cacheFactory(resourceManager, it)) }
            }
        }

        fun normalizeSearchText(text: String?): String {
            if (text == null) {
                return ""
            }
            val builder = StringBuilder()
            val lower = text.trim { it <= ' ' }.lowercase()
            var offset = 0
            while (offset < lower.length) {
                val codePoint = lower.codePointAt(offset)
                if (Character.isLetterOrDigit(codePoint) || codePoint == '_'.code || codePoint == ':'.code || codePoint == '.'.code) {
                    builder.appendCodePoint(codePoint)
                }
                offset += Character.charCount(codePoint)
            }
            return builder.toString()
        }

        fun fuzzySearchScore(query: String, candidate: String): Double {
            if (query.isEmpty() || candidate.isEmpty()) {
                return 0.0
            }
            var matched = 0
            var candidateIndex = 0
            for (i in 0..<query.length) {
                val ch = query.get(i)
                while (candidateIndex < candidate.length && candidate.get(candidateIndex) != ch) {
                    candidateIndex++
                }
                if (candidateIndex >= candidate.length) {
                    continue
                }
                matched++
                candidateIndex++
            }
            val containsQuery = candidate.contains(query)
            if (matched < 2 && !containsQuery) {
                return 0.0
            }
            var score = matched.toDouble() / candidate.length
            if (containsQuery) {
                score += 1.0
            }
            if (candidate.startsWith(query)) {
                score += 0.25
            }
            if (candidate == query) {
                score += 0.5
            }
            return score
        }
    }
}
