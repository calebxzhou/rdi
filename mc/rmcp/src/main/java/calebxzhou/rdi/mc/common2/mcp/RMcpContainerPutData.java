package calebxzhou.rdi.mc.common2.mcp;

public record RMcpContainerPutData(
        String format,
        boolean dryRun,
        int fromInventorySlot,
        int requestedCount,
        int movedCount,
        RMcpContainerData.Slot movedItem,
        RMcpInventoryData.Item beforeInventorySlot,
        RMcpInventoryData.Item afterInventorySlot,
        RMcpContainerMoveData.Endpoint to,
        RMcpInventoryData inventory,
        RMcpContainerData container
) {
}
