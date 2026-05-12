package calebxzhou.rdi.mc.common2.mcp;

public record RMcpItemUseOnBlockRequest(
        String pos,
        String face,
        String itemId,
        Integer fromInventorySlot,
        String hand,
        Integer times,
        boolean dryRun
) {
}
