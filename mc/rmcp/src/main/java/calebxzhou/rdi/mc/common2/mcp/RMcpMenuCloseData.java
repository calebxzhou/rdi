package calebxzhou.rdi.mc.common2.mcp;

public record RMcpMenuCloseData(
        boolean changed,
        boolean wasOpen,
        RMcpMenuData before,
        RMcpMenuData after
) {
}
