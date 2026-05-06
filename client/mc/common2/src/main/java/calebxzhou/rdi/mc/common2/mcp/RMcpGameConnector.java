package calebxzhou.rdi.mc.common2.mcp;

import calebxzhou.rdi.mc.common2.rcmd.client.RcmdRecipeView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface RMcpGameConnector {
    boolean playerInWorld();

    RMcpTestData testData();

    RMcpPosData posData();

    RMcpStaringBlockData staringBlockData(boolean includeFluid);

    RMcpBlockStateData blockStateData(String dim, int x, int y, int z);

    RMcpBlockEntityData blockEntityData(String dim, int x, int y, int z);

    RMcpHarvestToolData harvestToolData(String blockId, String dim, Integer x, Integer y, Integer z);

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
