package calebxzhou.rdi.mc.common2.mcp;

public record RMcpInventorySwapData(
        String format,
        String from,
        String to,
        boolean dryRun,
        boolean changed,
        RMcpInventoryData.Item beforeFrom,
        RMcpInventoryData.Item beforeTo,
        RMcpInventoryData.Item afterFrom,
        RMcpInventoryData.Item afterTo,
        RMcpInventoryData inventory
) {
}
