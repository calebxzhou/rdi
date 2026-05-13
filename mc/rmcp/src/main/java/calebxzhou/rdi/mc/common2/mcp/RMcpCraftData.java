package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpCraftData(
        String recipeId,
        String resultId,
        int requestedCount,
        int craftedCount,
        boolean dryRun,
        boolean changed,
        int outputSlot,
        RMcpInventoryData before,
        RMcpInventoryData after,
        List<String> warnings
) {
    public RMcpCraftData(String recipeId, String resultId, int requestedCount, int craftedCount, boolean dryRun, int outputSlot, RMcpInventoryData inventory) {
        this(recipeId, resultId, requestedCount, craftedCount, dryRun, !dryRun && craftedCount > 0, outputSlot, null, inventory, List.of());
    }
}
