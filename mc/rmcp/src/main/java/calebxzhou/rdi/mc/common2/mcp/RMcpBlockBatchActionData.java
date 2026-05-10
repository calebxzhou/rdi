package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlockBatchActionData(
        String action,
        List<FailedBlock> failedBlocks
) {
    public record FailedBlock(
            RMcpBlockPosData pos,
            String code
    ) {
    }
}
