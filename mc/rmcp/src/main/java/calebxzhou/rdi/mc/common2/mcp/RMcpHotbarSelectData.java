package calebxzhou.rdi.mc.common2.mcp;

public record RMcpHotbarSelectData(
        boolean dryRun,
        int requestedSlot,
        int beforeSlot,
        int afterSlot,
        RMcpInventoryData.Item beforeSelectedItem,
        RMcpInventoryData.Item afterSelectedItem,
        RMcpInventoryData inventory
) {
}
