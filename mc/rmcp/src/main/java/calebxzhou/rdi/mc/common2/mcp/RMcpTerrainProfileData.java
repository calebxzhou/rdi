package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpTerrainProfileData(
        String dim,
        String axis,
        RMcpBlockPosData center,
        int length,
        RMcpSectionSemanticData.BlockYRange scanY,
        Summary summary,
        Map<String, String> legend,
        String profile,
        List<Point> points
) {
    public record Summary(
            Integer minSurfaceY,
            Integer maxSurfaceY,
            int maxStep,
            boolean hasCliff,
            boolean hasOpenBelow,
            boolean hasFluid,
            int walkable,
            int blocked,
            int drops
    ) {
    }

    public record Point(
            int offset,
            RMcpBlockPosData pos,
            Integer surfaceY,
            Integer standY,
            String floorBlockId,
            String feetBlockId,
            String headBlockId,
            boolean walkable,
            Integer deltaFromPrev,
            String symbol,
            String note
    ) {
    }
}
