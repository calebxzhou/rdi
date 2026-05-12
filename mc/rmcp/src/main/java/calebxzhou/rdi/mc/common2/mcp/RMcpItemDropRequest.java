package calebxzhou.rdi.mc.common2.mcp;

public record RMcpItemDropRequest(
        String from,
        Integer count,
        Pos pos,
        Integer pickupDelay,
        boolean dryRun
) {
    public record Pos(
            String dim,
            Double x,
            Double y,
            Double z
    ) {
    }
}
