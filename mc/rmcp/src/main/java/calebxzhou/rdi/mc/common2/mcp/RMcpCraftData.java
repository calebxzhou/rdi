package calebxzhou.rdi.mc.common2.mcp;

public record RMcpCraftData(
        String format,
        String recipeId,
        String resultId,
        int requestedCount,
        int craftedCount,
        boolean dryRun,
        int outputSlot,
        RMcpInventoryData inventory
) {
}
