package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlocksFindData(
        String format,
        String dim,
        Center center,
        Range range,
        Scan scan,
        List<Match> matches
) {
    public record Center(RMcpBlockPosData block, int chunkX, int chunkZ, int sectionY) {
    }

    public record Range(int chunkRadius, Integer sectionRadius, String scanMode, int minSectionY, int maxSectionY) {
    }

    public record Scan(int loadedChunks, int skippedChunks, int sections, int blocks, int matched, int returned, boolean truncated) {
    }

    public record Match(String id, int count, RMcpBlockPosData nearest, List<RMcpBlockPosData> positions, List<String> states) {
    }
}
