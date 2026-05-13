package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlockBatchActionData(
        String action,
        boolean dryRun,
        boolean changed,
        List<FailedBlock> failedBlocks,
        List<String> warnings
) {
    public RMcpBlockBatchActionData(String action, List<FailedBlock> failedBlocks) {
        this(action, false, failedBlocks.isEmpty(), failedBlocks, List.of());
    }

    public record FailedBlock(
            RMcpBlockPosData pos,
            String code
    ) {
    }
}
