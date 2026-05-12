package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpRecipeData(
        String id,
        String source,
        String type,
        String category,
        String title,
        String runtimeClass,
        List<IngredientSlot> inputs,
        List<IngredientSlot> outputs,
        List<IngredientSlot> catalysts,
        List<IngredientSlot> renderOnly,
        Map<String, Object> extra
) {
    public record IngredientSlot(
            String role,
            List<Item> items,
            List<Fluid> fluids,
            List<ItemTag> tags
    ) {
        public IngredientSlot(String role, List<Item> items, List<Fluid> fluids) {
            this(role, items, fluids, List.of());
        }
    }

    public record ItemTag(
            String id,
            int count,
            int candidateCount,
            List<Item> examples,
            String source
    ) {
    }

    public record Item(
            String id,
            String langKey,
            int count,
            String snbt
    ) {
    }

    public record Fluid(
            String id,
            String name,
            long amount
    ) {
    }
}
