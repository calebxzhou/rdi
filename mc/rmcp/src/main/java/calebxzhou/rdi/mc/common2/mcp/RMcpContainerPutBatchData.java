package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerPutBatchData(
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
}
