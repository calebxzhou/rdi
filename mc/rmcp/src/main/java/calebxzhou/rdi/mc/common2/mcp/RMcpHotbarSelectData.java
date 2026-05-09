package calebxzhou.rdi.mc.common2.mcp;

public record RMcpHotbarSelectData(
        String format,
        boolean dryRun,
        int requestedSlot,
        int beforeSlot,
        int afterSlot,
        RMcpInventoryData.Item beforeSelectedItem,
        RMcpInventoryData.Item afterSelectedItem,
        RMcpInventoryData inventory
) {
}
