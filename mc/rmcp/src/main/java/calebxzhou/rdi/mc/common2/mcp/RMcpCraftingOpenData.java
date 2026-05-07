package calebxzhou.rdi.mc.common2.mcp;

public record RMcpCraftingOpenData(
        String format,
        boolean opened,
        boolean dryRun,
        String menu,
        int radius,
        RMcpBlockPosData tablePos
) {
}
