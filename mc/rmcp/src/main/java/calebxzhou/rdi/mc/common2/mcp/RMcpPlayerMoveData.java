package calebxzhou.rdi.mc.common2.mcp;

public record RMcpPlayerMoveData(
        boolean moved,
        RMcpPosData from,
        RMcpPosData to,
        double distance,
        boolean onGround
) {
}
