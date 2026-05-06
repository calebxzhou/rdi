package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpNearbyResourcesData(
        String format,
        String dim,
        Center center,
        Range range,
        Scan scan,
        List<Resource> resources,
        List<RMcpSectionSemanticData.BlockCount> topBlocks,
        Features features
) {
    public record Center(RMcpBlockPosData block, int chunkX, int chunkZ, int sectionY) {
    }

    public record Range(int chunkRadius, int sectionRadius) {
    }

    public record Scan(int loadedChunks, int skippedChunks, int sections, int blocks) {
    }

    public record Resource(
            String id,
            String category,
            int count,
            RMcpBlockPosData nearest,
            List<ResourceSection> sections
    ) {
    }

    public record ResourceSection(int chunkX, int chunkZ, int sectionY, int count) {
    }

    public record Features(
            boolean hasWater,
            boolean hasLava,
            boolean hasOre,
            boolean hasWood,
            boolean hasCrops,
            boolean hasBlockEntities
    ) {
    }
}
