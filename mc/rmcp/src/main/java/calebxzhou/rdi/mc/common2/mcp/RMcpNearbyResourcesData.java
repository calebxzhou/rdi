package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpNearbyResourcesData(
        String dim,
        Center center,
        Range range,
        Filter filter,
        Scan scan,
        List<Resource> resources,
        List<RMcpSectionSemanticData.BlockCount> topBlocks,
        Features features
) {
    public RMcpNearbyResourcesData(String dim, Center center, Range range, Scan scan, List<Resource> resources, List<RMcpSectionSemanticData.BlockCount> topBlocks, Features features) {
        this(dim, center, range, new Filter(List.of(), List.of(), 64), scan, resources, topBlocks, features);
    }

    public record Center(RMcpBlockPosData block, int chunkX, int chunkZ, int sectionY) {
    }

    public record Range(int chunkRadius, int sectionRadius) {
    }

    public record Filter(List<String> categories, List<String> ids, int limit) {
    }

    public record Scan(
            int chunkRadius,
            int sectionRadius,
            int requestedChunks,
            int loadedChunks,
            int skippedChunks,
            int requestedSections,
            int sections,
            int blocks,
            int matchedResources,
            int returnedResources,
            boolean limited,
            List<SkippedReason> skippedReasons
    ) {
        public Scan(int loadedChunks, int skippedChunks, int sections, int blocks) {
            this(0, 0, loadedChunks + skippedChunks, loadedChunks, skippedChunks, sections, sections, blocks, 0, 0, false, skippedChunks > 0 ? List.of(new SkippedReason("chunk_not_loaded", skippedChunks)) : List.of());
        }
    }

    public record SkippedReason(String reason, int count) {
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
