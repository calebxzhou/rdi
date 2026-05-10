package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpHarvestToolData(
        String stateSource,
        Block block,
        List<ToolScenario> scenarios,
        List<String> notes
) {
    public record Block(
            String id,
            String state,
            RMcpBlockPosData pos,
            boolean requiresCorrectToolForDrops,
            List<String> mineableWith,
            String minimumTier
    ) {
    }

    public record Tool(String id, String category, String tier, Map<String, Integer> enchantments) {
    }

    public record Drop(String id, int countMin, int countMax, String snbt) {
    }

    public record ToolScenario(
            Tool tool,
            boolean correctToolForDrops,
            boolean harvestable,
            List<Drop> drops
    ) {
    }
}
