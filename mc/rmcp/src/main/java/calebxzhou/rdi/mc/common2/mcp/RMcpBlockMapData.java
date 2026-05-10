package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpBlockMapData(
        String dim,
        String mode,
        RMcpBlockPosData center,
        int radius,
        Axes axes,
        Integer y,
        RMcpSectionSemanticData.BlockYRange scanY,
        Summary summary,
        Map<String, String> legend,
        List<String> rows,
        List<Cell> cells
) {
    public record Axes(String rows, String cols, String origin) {
    }

    public record Summary(
            int width,
            int height,
            int loadedChunks,
            int walkable,
            int blocked,
            int hazards,
            int fluids,
            int drops,
            List<RMcpSectionSemanticData.BlockCount> topBlocks
    ) {
    }

    public record Cell(
            RMcpBlockPosData pos,
            String symbol,
            String blockId,
            String floorBlockId,
            String note
    ) {
    }
}
