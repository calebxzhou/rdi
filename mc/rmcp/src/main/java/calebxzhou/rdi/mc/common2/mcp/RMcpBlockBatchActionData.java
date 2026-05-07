package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlockBatchActionData(
        String format,
        String action,
        int requestedCount,
        int changedCount,
        int failedCount,
        List<Result> results,
        RMcpInventoryData.Item mainHandBefore,
        RMcpInventoryData.Item mainHandAfter,
        RMcpInventoryData inventory
) {
    public record Result(
            RMcpBlockPosData pos,
            String code,
            boolean changed,
            String beforeBlockId,
            String afterBlockId
    ) {
    }
}
