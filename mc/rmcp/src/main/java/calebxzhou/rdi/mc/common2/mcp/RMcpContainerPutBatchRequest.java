package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerPutBatchRequest(
        List<Move> moves,
        boolean dryRun,
        boolean stopOnError
) {
    public record Move(
            Integer fromInventorySlot,
            RMcpContainerMoveBatchRequest.Endpoint to,
            int count
    ) {
    }
}
