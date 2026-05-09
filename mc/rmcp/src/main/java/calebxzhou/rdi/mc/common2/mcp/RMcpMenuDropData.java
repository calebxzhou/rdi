package calebxzhou.rdi.mc.common2.mcp;

public record RMcpMenuDropData(
        String format,
        boolean dryRun,
        int slot,
        int requestedCount,
        int droppedCount,
        boolean changed,
        RMcpMenuData.Slot beforeSlot,
        RMcpMenuData.Slot afterSlot,
        RMcpMenuData menu
) {
}
