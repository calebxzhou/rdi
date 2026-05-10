package calebxzhou.rdi.mc.common2.mcp;

public record RMcpContainerMoveData(
        boolean dryRun,
        int requestedCount,
        int movedCount,
        RMcpContainerData.Slot movedItem,
        Endpoint from,
        Endpoint to
) {
    public record Endpoint(
            String dim,
            RMcpBlockPosData pos,
            String side,
            Integer slot
    ) {
    }
}
