package calebxzhou.rdi.mc.common2.mcp;

public record RMcpContainerMoveData(
        String format,
        boolean dryRun,
        int requestedCount,
        int movedCount,
        RMcpContainerData.Slot movedItem,
        Endpoint from,
        Endpoint to,
        RMcpContainerData fromContainer,
        RMcpContainerData toContainer
) {
    public record Endpoint(
            String dim,
            RMcpBlockPosData pos,
            String side,
            Integer slot
    ) {
    }
}
