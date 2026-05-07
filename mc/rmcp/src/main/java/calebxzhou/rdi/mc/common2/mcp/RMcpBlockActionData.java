package calebxzhou.rdi.mc.common2.mcp;

public record RMcpBlockActionData(
        String format,
        String action,
        boolean changed,
        RMcpBlockPosData pos,
        String beforeBlockId,
        String afterBlockId,
        RMcpInventoryData.Item mainHandBefore,
        RMcpInventoryData.Item mainHandAfter,
        RMcpInventoryData inventory
) {
}
