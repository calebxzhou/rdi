package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpRecipeSummaryData(
        String itemId,
        int totalCount,
        int shownCount,
        int hiddenCount,
        List<Entry> recipes
) {
    public record Entry(
            String ref,
            String id,
            String source,
            String type,
            String kind,
            String category,
            String title,
            List<Item> inputItems,
            List<Fluid> inputFluids,
            List<Tag> inputTags,
            List<Item> outputItems,
            List<Fluid> outputFluids,
            List<Item> catalysts,
            String detail,
            String detailJson
    ) {
    }

    public record Item(String id, String name, int count) {
    }

    public record Fluid(String id, String name, long amount) {
    }

    public record Tag(String id, int count, int candidateCount) {
    }
}
