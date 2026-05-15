
package calebxzhou.rdi.mc.client.mcp

import calebxzhou.rdi.mc.common2.mcp.*
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Interface for connecting to the RMcp game and performing various game actions.
 */
interface RMcpGameConnector {
    fun playerInWorld(): Boolean

    fun modIds(): List<String>

    fun modData(id: String): RMcpModData

    fun questChapterList(): RQuestChapterList

    fun questChapter(id: String): RQuestChapter

    fun reachableQuests(): RReachableQuestList

    fun questDetail(id: String): RQuest

    fun posData(): REntityPosData

    fun inventoryData(): RMcpInventoryData

    fun inventoryTagMatchData(tag: String, scope: String, limit: Int): RMcpInventoryTagMatchData

    fun selectHotbarSlot(slot: Int, dryRun: Boolean): RMcpHotbarSelectData

    fun menuData(): RMcpMenuData

    fun closeMenu(): RMcpMenuCloseData

    fun dropMenuItem(slot: Int, count: Int, dryRun: Boolean): RMcpMenuDropData

    fun swapInventorySlots(from: String, to: String, dryRun: Boolean): RMcpInventorySwapData

    fun moveInventoryItems(from: String, to: String, count: Int, dryRun: Boolean): RMcpInventoryMoveData

    fun craft(
        slots: Map<String, Int>,
        shape: String,
        outputSlot: Int,
        times: Int,
        dryRun: Boolean
    ): RMcpCraftData

    fun craftParallel(request: RMcpCraftParallelRequest): RMcpCraftParallelData

    fun situationData(
        entityRadius: Double,
        resourceChunkRadius: Int,
        resourceSectionRadius: Int
    ): RMcpSituationData

    fun staringBlockData(includeFluid: Boolean): RMcpStaringBlockData

    fun blockStateData(x: Int, y: Int, z: Int): RMcpBlockStateData

    fun blockStateBatchData(positions: List<RBlockPos>): RMcpBlockStateBatchData

    fun blockEntityData(x: Int, y: Int, z: Int): RMcpBlockEntityData

    fun signTextData(x: Int, y: Int, z: Int, side: String): RMcpSignTextReadData

    fun setSignText(request: RMcpSignTextRequest): RMcpSignTextData

    fun harvestToolData(blockId: String, x: Int, y: Int, z: Int?): RMcpHarvestToolData

    fun containerData(pos: String, side: String): RMcpContainerData

    fun putInventoryItemIntoContainer(
        fromInventorySlot: Int,
        toPos: String,
        toSide: String,
        toSlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerPutData

    fun putInventoryItemsIntoContainerBatch(request: RMcpContainerPutBatchRequest): RMcpContainerPutBatchData

    fun takeContainerItemToInventory(
        fromPos: String,
        fromSide: String,
        fromSlot: Int,
        toInventorySlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerTakeData

    fun takeContainerItemsToInventoryBatch(request: RMcpContainerTakeBatchRequest): RMcpContainerTakeBatchData

    fun moveContainerItems(
        fromPos: String,
        fromSide: String,
        fromSlot: Int,
        toPos: String,
        toSide: String,
        toSlot: Int,
        count: Int,
        dryRun: Boolean
    ): RMcpContainerMoveData

    fun moveContainerItemsBatch(request: RMcpContainerMoveBatchRequest): RMcpContainerMoveBatchData

    fun placeBlock(x: Int, y: Int, z: Int, face: String): RMcpBlockActionData

    fun breakBlock(x: Int, y: Int, z: Int): RMcpBlockActionData

    fun useItemOnBlock(request: RMcpItemUseOnBlockRequest): RMcpItemUseOnBlockData

    fun placeBlocks(positions: List<RBlockPos>): RMcpBlockBatchActionData

    fun placeBlocksDiscrete(request: RMcpPlaceDiscreteRequest): RMcpBlockBatchActionData

    fun placeBlocksPalette(request: RMcpPlacePaletteRequest): RMcpBlockBatchActionData

    fun breakBlocks(positions: List<RBlockPos>): RMcpBlockBatchActionData

    fun placeBlockBox(request: RMcpPlaceBoxRequest): RMcpBlockBatchActionData

    fun placeBlockRing(request: RMcpPlaceRingRequest): RMcpBlockBatchActionData

    fun breakBlockBox(from: RBlockPos, to: RBlockPos, dryRun: Boolean): RMcpBlockBatchActionData

    fun movePlayer(x: Double, y: Double, z: Double): RMcpPlayerMoveData

    fun respawnPlayer(): RMcpRespawnData

    fun pickupItemEntities(ids: List<UUID>, radius: Double, limit: Int): RMcpItemPickupData

    fun dropInventoryItem(request: RMcpItemDropRequest): RMcpItemDropData

    fun blockMapSliceData(x: Int, y: Int, z: Int, radius: Int): RMcpBlockMapData

    fun blocksFindData(request: RMcpBlocksFindRequest): RMcpBlocksFindData

    fun nearbyResourcesData(
        x: Int,
        y: Int,
        z: Int,
        chunkRadius: Int,
        sectionRadius: Int,
        categories: List<String>,
        ids: List<String>,
        limit: Int
    ): RMcpNearbyResourcesData

    fun nearbyEntitiesData(
        x: Int,
        y: Int,
        z: Int,
        radius: Double,
        categories: List<String>,
        limit: Int
    ): RMcpNearbyEntitiesData

    fun staringEntityData(): RMcpEntityData

    fun entityData(uuid: UUID): RMcpEntityDetailData

    fun langKeyIndex(): RMcpLangKeyIndex

    fun itemSearchData(text: String, modId: String, limit: Int): RMcpItemSearchData

    fun blockSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData

    fun entityTypeSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData

    fun fluidSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData

    fun tagSearchData(text: String, modId: String, limit: Int): RMcpResolveSearchData

    fun playerData(uuid: UUID): RMcpPlayerData

    fun playerDetailData(uuid: UUID): RMcpPlayerDetailData

    fun mainHandItemData(): Map<String, Any>

    fun recipeData(itemId: String): List<RMcpRecipeData>

    fun screenshotPngData(): CompletableFuture<ByteArray>
}
