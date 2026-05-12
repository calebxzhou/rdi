package calebxzhou.rdi.mc.common2.mcp;

public record RMcpItemUseOnBlockData(
        String action,
        boolean dryRun,
        RMcpBlockPosData pos,
        String face,
        String requestedItemId,
        Integer requestedInventorySlot,
        String hand,
        int requestedTimes,
        int performedTimes,
        boolean changedBlock,
        String beforeBlockId,
        String afterBlockId,
        String beforeBlockState,
        String afterBlockState,
        RMcpInventoryData.Item itemBefore,
        RMcpInventoryData.Item itemAfter,
        RMcpInventoryData inventory
) {
}
