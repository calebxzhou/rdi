package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerTakeBatchData(
        String action,
        List<FailedMove> failedMoves
) {
    public record FailedMove(
            int index,
            String code
    ) {
    }
}
