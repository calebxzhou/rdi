package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerMoveBatchRequest(
        List<Move> moves,
        boolean dryRun,
        boolean stopOnError
) {
    public record Move(
            Endpoint from,
            Endpoint to,
            int count
    ) {
    }

    public record Endpoint(
            String pos,
            String side,
            Integer slot
    ) {
    }
}
