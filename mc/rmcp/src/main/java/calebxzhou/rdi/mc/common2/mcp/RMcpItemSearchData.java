package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpItemSearchData(
        String text,
        String modId,
        int limit,
        List<Result> results
) {
    public record Result(
            String itemId,
            String namespace,
            String langkey,
            String englishName,
            String chineseName,
            String modId,
            String modName,
            double score,
            String match
    ) {
    }
}
