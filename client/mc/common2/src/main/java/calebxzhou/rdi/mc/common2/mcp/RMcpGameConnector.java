package calebxzhou.rdi.mc.common2.mcp;

import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RMcpGameConnector {
    boolean playerInWorld();

    RMcpTestData testData();

    RMcpPosData posData();

    RMcpInventoryData inventoryData();

    RMcpInventorySwapData swapInventorySlots(String from, String to, boolean dryRun);

    RMcpInventoryMoveData moveInventoryItems(String from, String to, int count, boolean dryRun);

    RMcpCraftingOpenData openCrafting(int radius, boolean dryRun);

    RMcpCraftData craft(Map<String, Integer> slots, String shape, int outputSlot, int times, boolean dryRun);

    RMcpSituationData situationData(double entityRadius, int resourceChunkRadius, int resourceSectionRadius);

    RMcpStaringBlockData staringBlockData(boolean includeFluid);

    RMcpBlockStateData blockStateData(String dim, int x, int y, int z);

    RMcpBlockStateBatchData blockStateBatchData(String dim, List<RMcpBlockPosData> positions);

    RMcpBlockEntityData blockEntityData(String dim, int x, int y, int z);

    RMcpHarvestToolData harvestToolData(String blockId, String dim, Integer x, Integer y, Integer z);

    RMcpContainerData containerData(String pos, String side);

    RMcpContainerMoveData moveContainerItems(String fromPos, String fromSide, int fromSlot, String toPos, String toSide, Integer toSlot, int count, boolean dryRun);

    RMcpBlockActionData placeBlock(int x, int y, int z);

    RMcpBlockActionData breakBlock(int x, int y, int z);

    RMcpBlockBatchActionData placeBlocks(List<RMcpBlockPosData> positions);

    RMcpBlockBatchActionData breakBlocks(List<RMcpBlockPosData> positions);

    RMcpBlockBatchActionData placeBlockBox(RMcpBlockPosData from, RMcpBlockPosData to);

    RMcpBlockBatchActionData breakBlockBox(RMcpBlockPosData from, RMcpBlockPosData to);

    RMcpChunkSemanticData chunkData(int chunkX, int chunkZ);

    RMcpSectionSemanticData sectionData(int chunkX, int sectionY, int chunkZ);

    RMcpNearbyResourcesData nearbyResourcesData(String dim, int x, int y, int z, int chunkRadius, int sectionRadius);

    RMcpNearbyEntitiesData nearbyEntitiesData(String dim, int x, int y, int z, double radius, List<String> categories, int limit);

    RMcpEntityData staringEntityData();

    RMcpEntityDetailData entityData(java.util.UUID uuid);

    RMcpLangKeyIndex langKeyIndex();

    RMcpPlayerData playerData(java.util.UUID uuid);

    RMcpPlayerDetailData playerDetailData(java.util.UUID uuid);

    Map<String, Object> mainHandItemData();

    List<RcmdRecipeView> recipeData(String itemId);

    CompletableFuture<byte[]> screenshotPngData();
}
