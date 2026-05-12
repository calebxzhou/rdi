package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpInventoryTagMatchData(
        String tag,
        String scope,
        boolean containerOpen,
        int totalCount,
        int matchCount,
        List<Match> matches
) {
    public record Match(
            String source,
            String area,
            int slot,
            Integer menuSlot,
            RMcpInventoryData.Item item
    ) {
    }
}

