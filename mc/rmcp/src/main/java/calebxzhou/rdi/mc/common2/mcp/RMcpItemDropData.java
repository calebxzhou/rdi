package calebxzhou.rdi.mc.common2.mcp;

public record RMcpItemDropData(
        String code,
        String from,
        boolean dryRun,
        boolean changed,
        int requestedCount,
        int droppedCount,
        RMcpInventoryData.Item beforeItem,
        RMcpInventoryData.Item afterItem,
        DroppedEntity entity,
        RMcpInventoryData inventory
) {
    public record DroppedEntity(
            String uuid,
            String itemId,
            int count,
            String snbt,
            RMcpPosData pos
    ) {
    }
}
