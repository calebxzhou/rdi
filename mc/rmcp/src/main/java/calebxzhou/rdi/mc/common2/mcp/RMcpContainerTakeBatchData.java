package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerTakeBatchData(
        String format,
        boolean dryRun,
        boolean stopOnError,
        int requestedMoves,
        int succeededMoves,
        int failedMoves,
        int totalMovedCount,
        List<Result> results
) {
    public record Result(
            int index,
            String code,
            int requestedCount,
            int movedCount,
            RMcpContainerData.Slot movedItem,
            RMcpContainerMoveData.Endpoint from,
            Integer toInventorySlot,
            List<Integer> targetInventorySlots,
            RMcpInventoryData.Item beforeToInventorySlot,
            RMcpInventoryData.Item afterToInventorySlot,
            RMcpInventoryData inventory,
            RMcpContainerData container
    ) {
    }
}
