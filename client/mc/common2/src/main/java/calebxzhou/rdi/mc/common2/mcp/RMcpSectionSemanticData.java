package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpSectionSemanticData(
        String format,
        String dim,
        ChunkPos chunk,
        int sectionY,
        BlockYRange blockY,
        Summary summary,
        List<Layer> layers,
        Map<String, String> legend,
        Features features
) {
    public record ChunkPos(int x, int z) {
    }

    public record BlockYRange(int min, int max) {
    }

    public record BlockCount(String id, int count) {
    }

    public record NonAirBox(RMcpBlockPosData min, RMcpBlockPosData max) {
    }

    public record Summary(
            boolean empty,
            int nonAir,
            int paletteSize,
            List<BlockCount> topBlocks,
            NonAirBox bboxNonAir
    ) {
    }

    public record Layer(int y, List<String> grid, List<BlockCount> topBlocks) {
    }

    public record Features(boolean hasFluids, boolean hasBlockEntities, int solidRegions, int airRegions) {
    }
}
