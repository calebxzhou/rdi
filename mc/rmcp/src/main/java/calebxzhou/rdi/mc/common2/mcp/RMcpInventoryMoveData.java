package calebxzhou.rdi.mc.common2.mcp;

public record RMcpInventoryMoveData(
        String format,
        String from,
        String to,
        int count,
        boolean dryRun,
        boolean changed,
        int movedCount,
        RMcpInventoryData.Item beforeFrom,
        RMcpInventoryData.Item beforeTo,
        RMcpInventoryData.Item afterFrom,
        RMcpInventoryData.Item afterTo,
        RMcpInventoryData inventory
) {
}
