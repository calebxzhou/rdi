package calebxzhou.rdi.mc.common2.mcp;

public record RMcpCraftData(
        String recipeId,
        String resultId,
        int requestedCount,
        int craftedCount,
        boolean dryRun,
        int outputSlot,
        RMcpInventoryData inventory
) {
}
