package calebxzhou.rdi.mc.common2.mcp;

public record RMcpContainerPutData(
        boolean dryRun,
        int fromInventorySlot,
        int requestedCount,
        int movedCount,
        RMcpContainerData.Slot movedItem,
        RMcpContainerMoveData.Endpoint to
) {
}
