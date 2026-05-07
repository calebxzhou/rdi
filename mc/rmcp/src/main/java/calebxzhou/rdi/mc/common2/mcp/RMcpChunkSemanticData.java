package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpChunkSemanticData(
        String format,
        String dim,
        RMcpSectionSemanticData.ChunkPos chunk,
        Summary summary,
        List<Section> sections
) {
    public record Summary(
            int sections,
            int nonEmptySections,
            int nonAir,
            List<RMcpSectionSemanticData.BlockCount> topBlocks,
            RMcpSectionSemanticData.BlockYRange heightRangeNonAir
    ) {
    }

    public record Section(
            int sectionY,
            RMcpSectionSemanticData.BlockYRange blockY,
            boolean empty,
            int nonAir,
            List<RMcpSectionSemanticData.BlockCount> topBlocks,
            String sectionApi
    ) {
    }
}
