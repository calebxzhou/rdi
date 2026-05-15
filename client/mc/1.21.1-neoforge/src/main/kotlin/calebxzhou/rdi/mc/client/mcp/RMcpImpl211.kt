package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.client.RDIMain.Companion
import calebxzhou.rdi.mc.client.RDIMain.Companion.SCREENSHOT_EXECUTOR
import calebxzhou.rdi.mc.client.RDIMain.Companion.environmentData
import calebxzhou.rdi.mc.client.compat.jei.RJeiRecipeSource
import calebxzhou.rdi.mc.client.network.RMcpClientBridge
import calebxzhou.rdi.mc.client.rcmd.RcmdRecipeCodec211
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockBatchActionData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockEntityData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockMapData
import calebxzhou.rdi.mc.common2.mcp.RBlockPos
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateBatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlockStateEntryData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlocksFindData
import calebxzhou.rdi.mc.common2.mcp.RMcpBlocksFindRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveBatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveBatchRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerMoveData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerPutBatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerPutBatchRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerPutData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerTakeBatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerTakeBatchRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpContainerTakeData
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftData
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftParallelData
import calebxzhou.rdi.mc.common2.mcp.RMcpCraftParallelRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityData
import calebxzhou.rdi.mc.common2.mcp.RMcpEntityDetailData
import calebxzhou.rdi.mc.common2.mcp.RMcpFluidData
import calebxzhou.rdi.mc.common2.mcp.RMcpHarvestToolData
import calebxzhou.rdi.mc.common2.mcp.RMcpHotbarSelectData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryMoveData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventorySwapData
import calebxzhou.rdi.mc.common2.mcp.RMcpInventoryTagMatchData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemDropData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemDropRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpItemPickupData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemSearchData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemUseOnBlockData
import calebxzhou.rdi.mc.common2.mcp.RMcpItemUseOnBlockRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpLangKeyIndex
import calebxzhou.rdi.mc.common2.mcp.RMcpMenuCloseData
import calebxzhou.rdi.mc.common2.mcp.RMcpMenuData
import calebxzhou.rdi.mc.common2.mcp.RMcpMenuDropData
import calebxzhou.rdi.mc.common2.mcp.RMcpModData
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyEntitiesData
import calebxzhou.rdi.mc.common2.mcp.RMcpNearbyResourcesData
import calebxzhou.rdi.mc.common2.mcp.RMcpNoModError
import calebxzhou.rdi.mc.common2.mcp.RMcpNoPlayerError
import calebxzhou.rdi.mc.common2.mcp.RMcpPlaceBoxRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpPlaceDiscreteRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpPlacePaletteRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpPlaceRingRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerData
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerDetailData
import calebxzhou.rdi.mc.common2.mcp.RMcpPlayerMoveData
import calebxzhou.rdi.mc.common2.mcp.REntityPosData
import calebxzhou.rdi.mc.common2.mcp.RMcpRecipeData
import calebxzhou.rdi.mc.common2.mcp.RMcpResolveSearchData
import calebxzhou.rdi.mc.common2.mcp.RMcpRespawnData
import calebxzhou.rdi.mc.common2.mcp.RMcpSignTextData
import calebxzhou.rdi.mc.common2.mcp.RMcpSignTextReadData
import calebxzhou.rdi.mc.common2.mcp.RMcpSignTextRequest
import calebxzhou.rdi.mc.common2.mcp.RMcpSituationData
import calebxzhou.rdi.mc.common2.mcp.RMcpStaringBlockData
import calebxzhou.rdi.mc.common2.mcp.RQuest
import calebxzhou.rdi.mc.common2.mcp.RQuestChapter
import calebxzhou.rdi.mc.common2.mcp.RQuestChapterList
import calebxzhou.rdi.mc.common2.mcp.RReachableQuestList
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdIngredientView
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdItemStackView
import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.pipeline.RenderCall
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.fml.ModList
import net.neoforged.neoforgespi.language.IModInfo
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull
import kotlin.math.sqrt
import kotlin.use

class RMcpImpl211 : RMcpGameConnector {
    val mc get() = Minecraft.getInstance()
    override fun playerInWorld(): Boolean {
        val minecraft = Minecraft.getInstance()
        return minecraft.level != null && minecraft.player != null
    }

    override fun modIds(): MutableList<String> {
        return ModList.get().getSortedMods()
            .mapTo(ArrayList<String>()) { it.getModInfo().getModId() }
    }

    override fun modData(id: String): RMcpModData {
        return ModList.get().getModContainerById(id)
            .map { modData(it.getModInfo()) }.getOrNull() ?: throw RMcpNoModError()
    }

    override fun questChapterList(): RQuestChapterList {
        return  FtbQuestsMcpBridge.questChapterList()
    }

    override fun questChapter(id: String): RQuestChapter {
        return  FtbQuestsMcpBridge.questChapter(id)
    }

    override fun reachableQuests(): RReachableQuestList {
        return  FtbQuestsMcpBridge.reachableQuests()
    }

    override fun questDetail(id: String): RQuest {
        return  FtbQuestsMcpBridge.questDetail(id)
    }

    override fun posData(): REntityPosData {
        val player = Minecraft.getInstance().player ?: return null
        val dim = player.level().dimension().location().toString()
        return REntityPosData(
            dim,
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot()
        )
    }

    override fun inventoryData(): RMcpInventoryData {
        mc.player?.inventory?.let { inv->
            listOf(inv.items,inv.armor,inv.offhand).map { l -> l.map { stack -> stack.toString() } }
                .let { (i,a,o) -> RMcpInventoryData(i,a,o) }
        }
        throw RMcpNoPlayerError()
    }

    override fun inventoryTagMatchData(
        tag: String,
        scope: String,
        limit: Int
    ): RMcpInventoryTagMatchData {
        return Companion.inventoryTagMatchData(Minecraft.getInstance(), tag, scope, limit)
    }

    override fun selectHotbarSlot(slot: Int, dryRun: Boolean): RMcpHotbarSelectData {
        return RMcpClientBridge.requestHotbarSelect(slot, dryRun)
    }

    override fun menuData(): RMcpMenuData {
        return RMcpClientBridge.requestMenu()
    }

    override fun closeMenu(): RMcpMenuCloseData {
        return RMcpClientBridge.requestMenuClose()
    }

    override fun dropMenuItem(slot: Int, count: Int, dryRun: Boolean): RMcpMenuDropData {
        return RMcpClientBridge.requestMenuDrop(slot, count, dryRun)
    }

    override fun swapInventorySlots(from: String, to: String, dryRun: Boolean): RMcpInventorySwapData {
        return Companion.swapInventorySlots(Minecraft.getInstance(), from, to, dryRun)
    }

    override fun moveInventoryItems(
        from: String,
        to: String,
        count: Int,
        dryRun: Boolean
    ): RMcpInventoryMoveData {
        return Companion.moveInventoryItems(Minecraft.getInstance(), from, to, count, dryRun)
    }

    override fun craft(
        slots: MutableMap<String, Int>,
        shape: String,
        outputSlot: Int,
        times: Int,
        dryRun: Boolean
    ): RMcpCraftData {
        return RMcpClientBridge.requestCraft(slots, shape, outputSlot, times, dryRun)
    }

    override fun craftParallel(request: RMcpCraftParallelRequest): RMcpCraftParallelData {
        return RMcpClientBridge.requestCraftParallel(request)
    }

    override fun situationData(
        entityRadius: Double,
        resourceChunkRadius: Int,
        resourceSectionRadius: Int
    ): RMcpSituationData {
        val level = mc.level
        val player = mc.player
        if (level == null || player == null) {

        }
        val dim = level.dimension().location().toString()
        val pos = player.blockPosition()
        val inventory = Companion.inventoryData(minecraft) ?: return null
        val nearbyEntities = Companion.nearbyEntitiesData(
            minecraft,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            entityRadius,
            mutableListOf<String>("monster", "animal"),
            12
        ) ?: return null
        val nearbyResources = Companion.nearbyResourcesData(
            minecraft,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            resourceChunkRadius,
            resourceSectionRadius
        ) ?: return null
        return RMcpSituationData(
            Companion.playerBrief(minecraft, player),
            environmentData(level, player),
            RMcpSituationData.Inventory(
                inventory.selectedHotbarSlot,
                inventory.selectedItem,
                inventory.armor,
                inventory.offhand.orEmpty().firstOrNull(),
                inventory.summary
            ),
            RMcpSituationData.Nearby(
                RMcpSituationData.Range(entityRadius, resourceChunkRadius, resourceSectionRadius),
                nearbyEntities.summary,
                nearbyEntities.entities,
                nearbyResources.features,
                nearbyResources.resources.orEmpty()
                    .take(12)
                    .toCollection(ArrayList<RMcpNearbyResourcesData.Resource>())
            )
        )
    }

    override fun mainHandItemData(): MutableMap<String, Any> {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (minecraft.level == null || player == null) {
            return null
        }
        return RMcpMcDataCodec211.itemStackToSnbtMap(
            player.getMainHandItem(),
            minecraft.level!!.registryAccess()
        )
    }

    override fun screenshotPngData(): CompletableFuture<ByteArray> {
        val minecraft = Minecraft.getInstance()
        val future = CompletableFuture<ByteArray>()
        val capture = RenderCall {
            try {
                val image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())
                SCREENSHOT_EXECUTOR.execute(Runnable {
                    try {
                        image.use {
                            future.complete(image.asByteArray())
                        }
                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                })
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        if (RenderSystem.isOnRenderThread()) {
            capture.execute()
        } else {
            RenderSystem.recordRenderCall(capture)
        }
        return future
    }

    override fun recipeData(itemId: String): MutableList<RMcpRecipeData> {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return null
        }
        val registries = minecraft.level!!.registryAccess()
        val recipes = ArrayList<RMcpRecipeData>()
        for (holder in minecraft.level!!.getRecipeManager().getOrderedRecipes()) {
            val recipe: Recipe<*> = holder.value()
            val result = recipe.getResultItem(registries)
            if (result.isEmpty() || itemId != RcmdRecipeCodec211.itemId(result)) {
                continue
            }
            recipes.add(
                minecraftRecipeData(
                    RcmdRecipeCodec211.recipeView(
                        holder.id().toString(),
                        recipe,
                        result
                    )
                )
            )
        }
        if (ModList.get().isLoaded("jei")) {
            recipes.addAll(RJeiRecipeSource.queryByOutputItem(itemId))
        }
        return List.copyOf<RMcpRecipeData>(recipes)
    }

    override fun staringBlockData(includeFluid: Boolean): RMcpStaringBlockData {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (minecraft.level == null || player == null) {
            return null
        }
        val picked = player.pick(256.0, 0.0f, includeFluid)
        if (picked !is BlockHitResult || picked.getType() != HitResult.Type.BLOCK) {
            return null
        }
        val pos = picked.getBlockPos()
        val blockState = minecraft.level!!.getBlockState(pos)
        val blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()
        var fluid: RMcpFluidData = null
        if (includeFluid) {
            val fluidState = minecraft.level!!.getFluidState(pos)
            if (!fluidState.isEmpty()) {
                val fluidId = BuiltInRegistries.FLUID.getKey(fluidState.getType()).toString()
                fluid =
                    RMcpFluidData(fluidId, RMcpMcDataCodec211.stateString(fluidId, fluidState.toString()))
            }
        }
        return RMcpStaringBlockData(
            minecraft.level!!.dimension().location().toString(),
            RBlockPos(pos.getX(), pos.getY(), pos.getZ()),
            blockId,
            RMcpMcDataCodec211.stateString(blockId, blockState.toString()),
            fluid
        )
    }

    override fun blockStateData(x: Int, y: Int, z: Int): RMcpBlockStateData {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return null
        }
        val dim = minecraft.level!!.dimension().location().toString()
        val pos = BlockPos(x, y, z)
        val blockState = minecraft.level!!.getBlockState(pos)
        val blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()
        return RMcpBlockStateData(
            dim,
            RBlockPos(pos.getX(), pos.getY(), pos.getZ()),
            blockId,
            RMcpMcDataCodec211.stateString(blockId, blockState.toString())
        )
    }

    override fun blockStateBatchData(positions: MutableList<RBlockPos>): RMcpBlockStateBatchData {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return null
        }
        val dim = minecraft.level!!.dimension().location().toString()
        val blocks = ArrayList<RMcpBlockStateEntryData>()
        for (posData in positions) {
            val pos = BlockPos(posData.x, posData.y, posData.z)
            val blockState = minecraft.level!!.getBlockState(pos)
            val blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString()
            blocks.add(
                RMcpBlockStateEntryData(
                    RBlockPos(pos.getX(), pos.getY(), pos.getZ()),
                    blockId,
                    RMcpMcDataCodec211.stateString(blockId, blockState.toString())
                )
            )
        }
        return RMcpBlockStateBatchData(dim, positions.size, blocks)
    }

    override fun blockEntityData(x: Int, y: Int, z: Int): RMcpBlockEntityData {
        return RMcpClientBridge.requestBlockEntity(x, y, z)
    }

    override fun signTextData(x: Int, y: Int, z: Int, side: String): RMcpSignTextReadData {
        return RMcpClientBridge.requestSignTextRead(x, y, z, side)
    }

    override fun setSignText(request: RMcpSignTextRequest): RMcpSignTextData {
        return RMcpClientBridge.requestSignText(request)
    }

    override fun harvestToolData(blockId: String, x: Int, y: Int, z: Int): RMcpHarvestToolData {
        return RMcpClientBridge.requestHarvestTool(blockId, x, y, z)
    }

    override fun containerData(pos: String, side: String): RMcpContainerData {
        return RMcpClientBridge.requestContainer(pos, side)
    }

    override fun putInventoryItemIntoContainer(
        fromInventorySlot: Int,
        toPos: String,
        toSide: String,
        toSlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerPutData {
        return RMcpClientBridge.requestContainerPut(fromInventorySlot, toPos, toSide, toSlot, count, dryRun)
    }

    override fun putInventoryItemsIntoContainerBatch(request: RMcpContainerPutBatchRequest): RMcpContainerPutBatchData {
        return RMcpClientBridge.requestContainerPutBatch(request)
    }

    override fun takeContainerItemToInventory(
        fromPos: String,
        fromSide: String,
        fromSlot: Int,
        toInventorySlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerTakeData {
        return RMcpClientBridge.requestContainerTake(
            fromPos,
            fromSide,
            fromSlot,
            toInventorySlot,
            count,
            dryRun
        )
    }

    override fun takeContainerItemsToInventoryBatch(request: RMcpContainerTakeBatchRequest): RMcpContainerTakeBatchData {
        return RMcpClientBridge.requestContainerTakeBatch(request)
    }

    override fun moveContainerItems(
        fromPos: String,
        fromSide: String,
        fromSlot: Int,
        toPos: String,
        toSide: String,
        toSlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerMoveData {
        return RMcpClientBridge.requestContainerMove(
            fromPos,
            fromSide,
            fromSlot,
            toPos,
            toSide,
            toSlot,
            count,
            dryRun
        )
    }

    override fun moveContainerItemsBatch(request: RMcpContainerMoveBatchRequest): RMcpContainerMoveBatchData {
        return RMcpClientBridge.requestContainerMoveBatch(request)
    }

    override fun placeBlock(x: Int, y: Int, z: Int, face: String): RMcpBlockActionData {
        return RMcpClientBridge.requestPlaceBlock(x, y, z, face)
    }

    override fun breakBlock(x: Int, y: Int, z: Int): RMcpBlockActionData {
        return RMcpClientBridge.requestBreakBlock(x, y, z)
    }

    override fun useItemOnBlock(request: RMcpItemUseOnBlockRequest): RMcpItemUseOnBlockData {
        return RMcpClientBridge.requestItemUseOnBlock(request)
    }

    override fun placeBlocks(positions: MutableList<RBlockPos>): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestPlaceBlocks(positions)
    }

    override fun placeBlocksDiscrete(request: RMcpPlaceDiscreteRequest): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestPlaceBlocksDiscrete(request)
    }

    override fun placeBlocksPalette(request: RMcpPlacePaletteRequest): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestPlaceBlocksPalette(request)
    }

    override fun breakBlocks(positions: MutableList<RBlockPos>): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestBreakBlocks(positions)
    }

    override fun placeBlockBox(request: RMcpPlaceBoxRequest): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestPlaceBlockBox(request)
    }

    override fun placeBlockRing(request: RMcpPlaceRingRequest): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestPlaceBlockRing(request)
    }

    override fun breakBlockBox(
        from: RBlockPos,
        to: RBlockPos,
        dryRun: Boolean
    ): RMcpBlockBatchActionData {
        return RMcpClientBridge.requestBreakBlockBox(from, to, dryRun)
    }

    override fun movePlayer(x: Double, y: Double, z: Double): RMcpPlayerMoveData {
        return RMcpClientBridge.requestMovePlayer(x, y, z)
    }

    override fun respawnPlayer(): RMcpRespawnData {
        return RMcpClientBridge.requestRespawn()
    }

    override fun pickupItemEntities(
        ids: MutableList<UUID>,
        radius: Double,
        limit: Int
    ): RMcpItemPickupData {
        return RMcpClientBridge.requestPickupItemEntities(ids, radius, limit)
    }

    override fun dropInventoryItem(request: RMcpItemDropRequest): RMcpItemDropData {
        return RMcpClientBridge.requestDropInventoryItem(request)
    }

    override fun blockMapSliceData(x: Int, y: Int, z: Int, radius: Int): RMcpBlockMapData {
        return Companion.blockMapSliceData(Minecraft.getInstance(), x, y, z, radius)
    }

    override fun blocksFindData(request: RMcpBlocksFindRequest): RMcpBlocksFindData {
        return Companion.blocksFindData(Minecraft.getInstance(), request)
    }

    override fun nearbyResourcesData(
        x: Int,
        y: Int,
        z: Int,
        chunkRadius: Int,
        sectionRadius: Int,
        categories: MutableList<String>,
        ids: MutableList<String>,
        limit: Int
    ): RMcpNearbyResourcesData {
        return Companion.nearbyResourcesData(
            Minecraft.getInstance(),
            x,
            y,
            z,
            chunkRadius,
            sectionRadius,
            categories,
            ids,
            limit
        )
    }

    override fun nearbyEntitiesData(
        x: Int,
        y: Int,
        z: Int,
        radius: Double,
        categories: MutableList<String>,
        limit: Int
    ): RMcpNearbyEntitiesData {
        return Companion.nearbyEntitiesData(Minecraft.getInstance(), x, y, z, radius, categories, limit)
    }

    override fun staringEntityData(): RMcpEntityData {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (minecraft.level == null || player == null) {
            return null
        }
        val range = 64.0
        val eye = player.getEyePosition(0.0f)
        val view = player.getViewVector(0.0f)
        val end = eye.add(view.scale(range))
        val blockHit = player.pick(range, 0.0f, false)
        val maxDistanceSqr =
            if (blockHit.getType() == HitResult.Type.MISS) range * range else eye.distanceToSqr(blockHit.getLocation())
        val box = player.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0)
        val hit = ProjectileUtil.getEntityHitResult(
            player,
            eye,
            end,
            box,
            Predicate { entity: Entity -> !entity!!.isSpectator() && entity.isPickable() },
            maxDistanceSqr
        )
        if (hit == null) {
            return null
        }
        val entity = hit.getEntity()
        val dim = minecraft.level!!.dimension().location().toString()
        val type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
        return RMcpEntityData(
            dim,
            entity.getStringUUID(),
            type,
            entity.getName().getString(),
            REntityPosData(
                dim,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                entity.getYRot(),
                entity.getXRot()
            ),
            sqrt(player.distanceToSqr(entity))
        )
    }

    override fun entityData(uuid: UUID): RMcpEntityDetailData {
        val minecraft = Minecraft.getInstance()
        if (minecraft.level == null) {
            return null
        }
        for (entity in minecraft.level!!.entitiesForRendering()) {
            if (entity.getUUID() == uuid) {
                return RMcpMcDataCodec211.entityDetail(
                    minecraft.level!!.dimension().location().toString(),
                    entity,
                    minecraft.level!!.registryAccess()
                )
            }
        }
        return null
    }

    override fun langKeyIndex(): RMcpLangKeyIndex {
        return Companion.langKeyIndex(Minecraft.getInstance())
    }

    override fun itemSearchData(text: String, modId: String, limit: Int): RMcpItemSearchData {
        return Companion.itemSearchData(Minecraft.getInstance(), text, modId, limit)
    }

    override fun blockSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData {
        return Companion.blockSearchData(Minecraft.getInstance(), text, modId, limit)
    }

    override fun entityTypeSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData {
        return Companion.entityTypeSearchData(Minecraft.getInstance(), text, modId, limit)
    }

    override fun fluidSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData {
        return Companion.fluidSearchData(Minecraft.getInstance(), text, modId, limit)
    }

    override fun tagSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData {
        return Companion.tagSearchData(Minecraft.getInstance(), text, modId, limit)
    }

    override fun playerData(uuid: UUID): RMcpPlayerData {
        val minecraft = Minecraft.getInstance()
        val player: Player = findPlayer(minecraft, uuid)
        if (minecraft.level == null || player == null) {
            return null
        }
        return playerBrief(minecraft, player)
    }

    override fun playerDetailData(uuid: UUID): RMcpPlayerDetailData {
        val minecraft = Minecraft.getInstance()
        val player: Player = findPlayer(minecraft, uuid)
        if (minecraft.level == null || player == null) {
            return null
        }
        val dim = minecraft.level!!.dimension().location().toString()
        return RMcpPlayerDetailData(
            playerBrief(minecraft, player),
            RMcpMcDataCodec211.entityDetail(dim, player, minecraft.level!!.registryAccess()),
            profileMap(player.getGameProfile()),
            playerMap(minecraft, player),
            inventoryMap(player, minecraft.level!!.registryAccess())
        )
    }

    private fun modData(mod: IModInfo): RMcpModData {
        val dependencies = mod.dependencies
            .mapTo(ArrayList<RMcpModData.Dependency>()) { dep ->
                RMcpModData.Dependency(
                    dep.modId,
                    dep.versionRange.toString(),
                    dep.type.name.lowercase(),
                    dep.ordering.name.lowercase(),
                    dep.side.name.lowercase()
                )
            }
        return RMcpModData(
            mod.modId,
            mod.displayName,
            mod.version.toString(),
            mod.description,
            dependencies
        )
    }

    private fun minecraftRecipeData(recipe: RcmdRecipeView): RMcpRecipeData {
        val inputs = ArrayList<RMcpRecipeData.IngredientSlot>()
        if (recipe.ingredients != null) {
            for (ingredient in recipe.ingredients) {
                inputs.add(recipeIngredientSlot("input", ingredient))
            }
        }
        if (recipe.key != null) {
            for (ingredient in recipe.key.values) {
                inputs.add(recipeIngredientSlot("input", ingredient))
            }
        }
        val outputs = mutableListOf<RMcpRecipeData.IngredientSlot>(
            RMcpRecipeData.IngredientSlot(
                "output",
                mutableListOf<RMcpRecipeData.Item>(recipeItem(recipe.result)),
                mutableListOf<RMcpRecipeData.Fluid>(),
                mutableListOf<RMcpRecipeData.ItemTag>()
            )
        )
        val extra = LinkedHashMap<String, Any?>()
        if (recipe.pattern != null) {
            extra["pattern"] = recipe.pattern
        }
        if (recipe.key != null && !recipe.key.isEmpty()) {
            val key = LinkedHashMap<String, Any?>()
            for (entry in recipe.key.entries) {
                key[entry.key] = recipeIngredientSlot("input", entry.value)
            }
            extra["key"] = LinkedHashMap(key)
        }
        if (recipe.experience != null) {
            extra["experience"] = recipe.experience
        }
        if (recipe.cookingTime != null) {
            extra["cookingTime"] = recipe.cookingTime
        }
        return RMcpRecipeData(
            recipe.id,
            "minecraft",
            recipe.type,
            recipe.category ?: recipe.group ?: "",
            recipe.result.langKey,
            "",
            inputs,
            outputs,
            mutableListOf<RMcpRecipeData.IngredientSlot>(),
            mutableListOf<RMcpRecipeData.IngredientSlot>(),
            extra
        )
    }

    private fun recipeIngredientSlot(role: String, ingredient: RcmdIngredientView): RMcpRecipeData.IngredientSlot {
        val items = ingredient.items
            ?.mapTo(ArrayList<RMcpRecipeData.Item>()) { recipeItem(it) }
            ?: mutableListOf<RMcpRecipeData.Item>()
        val tags = ingredient.tags
            ?.mapTo(ArrayList<RMcpRecipeData.ItemTag>()) { recipeItemTag(it) }
            ?: mutableListOf<RMcpRecipeData.ItemTag>()
        return RMcpRecipeData.IngredientSlot(role, items, mutableListOf<RMcpRecipeData.Fluid>(), tags)
    }

    private fun recipeItemTag(tag: RcmdIngredientView.ItemTag): RMcpRecipeData.ItemTag {
        val examples = tag.examples
            ?.mapTo(ArrayList<RMcpRecipeData.Item>()) { recipeItem(it) }
            ?: mutableListOf<RMcpRecipeData.Item>()
        return RMcpRecipeData.ItemTag(tag.id, tag.count, tag.candidateCount, examples, "recipe")
    }

    private fun recipeItem(item: RcmdItemStackView): RMcpRecipeData.Item {
        return RMcpRecipeData.Item(item.id, item.langKey, item.count, null)
    }

    private fun findPlayer(minecraft: Minecraft, uuid: UUID?): Player? {
        if (uuid == null) {
            return minecraft.player
        }
        if (minecraft.level == null) {
            return null
        }
        for (entity in minecraft.level!!.entitiesForRendering()) {
            if (entity is Player && entity.getUUID() == uuid) {
                return entity
            }
        }
        return null
    }

    private fun playerBrief(minecraft: Minecraft, player: Player): RMcpPlayerData {
        val dim = player.level().dimension().location().toString()
        val playerInfo = minecraft.getConnection()?.getPlayerInfo(player.getUUID())
        return RMcpPlayerData(
            dim,
            player.getStringUUID(),
            player.getName().getString(),
            REntityPosData(dim, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()),
            player.getHealth(),
            player.getMaxHealth(),
            player.getFoodData().getFoodLevel(),
            playerInfo?.getGameMode()?.getName(),
            playerInfo?.getLatency()
        )
    }

    private fun profileMap(profile: GameProfile): MutableMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = profile.getId()?.toString()
        map["name"] = profile.getName()
        val properties = ArrayList<MutableMap<String, Any?>>()
        for (property in profile.getProperties().values()) {
            val propertyMap = LinkedHashMap<String, Any?>()
            propertyMap["name"] = property.name()
            propertyMap["value"] = property.value()
            propertyMap["signature"] = property.signature()
            propertyMap["hasSignature"] = property.hasSignature()
            properties.add(propertyMap)
        }
        map["properties"] = properties
        return map
    }

    private fun playerMap(minecraft: Minecraft, player: Player): MutableMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val playerInfo = minecraft.getConnection()?.getPlayerInfo(player.getUUID())
        map["score"] = player.getScore()
        map["experienceLevel"] = player.experienceLevel
        map["totalExperience"] = player.totalExperience
        map["experienceProgress"] = player.experienceProgress
        map["mayBuild"] = player.mayBuild()
        if (playerInfo != null) {
            map["gameMode"] = playerInfo.getGameMode()?.getName()
            map["latency"] = playerInfo.getLatency()
            map["tabListName"] = playerInfo.getTabListDisplayName()?.getString()
        }
        val food = player.getFoodData()
        val foodMap = LinkedHashMap<String, Any?>()
        foodMap["level"] = food.getFoodLevel()
        foodMap["saturation"] = food.getSaturationLevel()
        foodMap["exhaustion"] = food.getExhaustionLevel()
        map["food"] = foodMap
        val abilities = player.getAbilities()
        val abilitiesMap = LinkedHashMap<String, Any?>()
        abilitiesMap["invulnerable"] = abilities.invulnerable
        abilitiesMap["flying"] = abilities.flying
        abilitiesMap["mayfly"] = abilities.mayfly
        abilitiesMap["instabuild"] = abilities.instabuild
        abilitiesMap["mayBuild"] = abilities.mayBuild
        abilitiesMap["flyingSpeed"] = abilities.getFlyingSpeed()
        map["abilities"] = abilitiesMap
        return map
    }

    private fun inventoryMap(player: Player, registryAccess: HolderLookup.Provider): MutableMap<String, Any?> {
        val inventory = player.getInventory()
        val map = LinkedHashMap<String, Any?>()
        map["selected"] = inventory.selected
        map["selectedItem"] = RMcpMcDataCodec211.itemStackToMap(inventory.getSelected(), registryAccess)
        map["items"] = itemStackList(inventory.items, registryAccess)
        map["armor"] = itemStackList(inventory.armor, registryAccess)
        map["offhand"] = itemStackList(inventory.offhand, registryAccess)
        return map
    }

    private fun itemStackList(
        stacks: MutableList<ItemStack>,
        registryAccess: HolderLookup.Provider
    ): MutableList<MutableMap<String, Any?>?> {
        val list = ArrayList<MutableMap<String, Any?>?>()
        for (i in stacks.indices) {
            val stack = RMcpMcDataCodec211.itemStackToMap(stacks[i], registryAccess)
            if (stack != null) {
                stack["slot"] = i
                list.add(stack)
            }
        }
        return list
    }
}
