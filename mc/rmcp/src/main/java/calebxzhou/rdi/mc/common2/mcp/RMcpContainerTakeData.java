package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerTakeData(
        boolean dryRun,
        int requestedCount,
        int movedCount,
        RMcpContainerData.Slot movedItem,
        RMcpContainerMoveData.Endpoint from,
        Integer toInventorySlot,
        List<Integer> targetInventorySlots
) {
}
