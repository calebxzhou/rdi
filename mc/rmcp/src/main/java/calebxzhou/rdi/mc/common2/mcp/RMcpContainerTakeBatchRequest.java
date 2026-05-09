package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerTakeBatchRequest(
        List<Move> moves,
        boolean dryRun,
        boolean stopOnError
) {
    public record Move(
            RMcpContainerMoveBatchRequest.Endpoint from,
            Integer toInventorySlot,
            int count
    ) {
    }
}
