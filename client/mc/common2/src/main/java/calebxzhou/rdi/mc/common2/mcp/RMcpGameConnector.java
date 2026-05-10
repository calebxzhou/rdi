package calebxzhou.rdi.mc.common2.mcp;

import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RMcpGameConnector {
    boolean playerInWorld();

    RMcpTestData testData();

    List<String> modIds();

    RMcpModData modData(String id);

    RQuestChapterList questChapterList();

    RQuestChapter questChapter(String id);

    RReachableQuestList reachableQuests();

    RMcpPosData posData();

    RMcpInventoryData inventoryData();

    RMcpHotbarSelectData selectHotbarSlot(int slot, boolean dryRun);

    RMcpMenuData menuData();

    RMcpMenuDropData dropMenuItem(int slot, int count, boolean dryRun);

    RMcpInventorySwapData swapInventorySlots(String from, String to, boolean dryRun);

    RMcpInventoryMoveData moveInventoryItems(String from, String to, int count, boolean dryRun);

    RMcpCraftData craft(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun);

    RMcpSituationData situationData(double entityRadius, int resourceChunkRadius, int resourceSectionRadius);

    RMcpStaringBlockData staringBlockData(boolean includeFluid);

    RMcpBlockStateData blockStateData(int x, int y, int z);

    RMcpBlockStateBatchData blockStateBatchData(List<RMcpBlockPosData> positions);

    RMcpBlockEntityData blockEntityData(String dim, int x, int y, int z);

    RMcpSignTextReadData signTextData(int x, int y, int z, String side);

    RMcpSignTextData setSignText(RMcpSignTextRequest request);

    RMcpHarvestToolData harvestToolData(String blockId, String dim, Integer x, Integer y, Integer z);

    RMcpContainerData containerData(String pos, String side);

    RMcpContainerPutData putInventoryItemIntoContainer(int fromInventorySlot, String toPos, String toSide, Integer toSlot, int count, boolean dryRun);

    RMcpContainerPutBatchData putInventoryItemsIntoContainerBatch(RMcpContainerPutBatchRequest request);

    RMcpContainerTakeData takeContainerItemToInventory(String fromPos, String fromSide, int fromSlot, Integer toInventorySlot, int count, boolean dryRun);

    RMcpContainerTakeBatchData takeContainerItemsToInventoryBatch(RMcpContainerTakeBatchRequest request);

    RMcpContainerMoveData moveContainerItems(String fromPos, String fromSide, int fromSlot, String toPos, String toSide, Integer toSlot, int count, boolean dryRun);

    RMcpContainerMoveBatchData moveContainerItemsBatch(RMcpContainerMoveBatchRequest request);

    RMcpBlockActionData placeBlock(int x, int y, int z, String face);

    RMcpBlockActionData breakBlock(int x, int y, int z);

    RMcpBlockBatchActionData placeBlocks(List<RMcpBlockPosData> positions);

    RMcpBlockBatchActionData placeBlocksDiscrete(RMcpPlaceDiscreteRequest request);

    RMcpBlockBatchActionData placeBlocksPalette(RMcpPlacePaletteRequest request);

    RMcpBlockBatchActionData breakBlocks(List<RMcpBlockPosData> positions);

    RMcpBlockBatchActionData placeBlockBox(RMcpPlaceBoxRequest request);

    RMcpBlockBatchActionData breakBlockBox(RMcpBlockPosData from, RMcpBlockPosData to);

    RMcpPlayerMoveData movePlayer(double x, double y, double z);

    RMcpRespawnData respawnPlayer();

    RMcpItemPickupData pickupItemEntities(List<UUID> ids, double radius, int limit);

    RMcpChunkSemanticData chunkData(int chunkX, int chunkZ);

    RMcpSectionSemanticData sectionData(int chunkX, int sectionY, int chunkZ);

    RMcpBlockMapData blockMapSliceData(Integer x, Integer y, Integer z, int radius);

    RMcpBlockMapData blockMapWalkableData(Integer x, Integer y, Integer z, int radius);

    RMcpTerrainProfileData terrainProfileData(String axis, Integer x, Integer y, Integer z, int length, int verticalRadius);

    RMcpBlocksFindData blocksFindData(RMcpBlocksFindRequest request);

    RMcpNearbyResourcesData nearbyResourcesData(String dim, int x, int y, int z, int chunkRadius, int sectionRadius);

    RMcpNearbyEntitiesData nearbyEntitiesData(String dim, int x, int y, int z, double radius, List<String> categories, int limit);

    RMcpEntityData staringEntityData();

    RMcpEntityDetailData entityData(UUID uuid);

    RMcpLangKeyIndex langKeyIndex();

    RMcpPlayerData playerData(UUID uuid);

    RMcpPlayerDetailData playerDetailData(UUID uuid);

    Map<String, Object> mainHandItemData();

    List<RcmdRecipeView> recipeData(String itemId);

    CompletableFuture<byte[]> screenshotPngData();
}
