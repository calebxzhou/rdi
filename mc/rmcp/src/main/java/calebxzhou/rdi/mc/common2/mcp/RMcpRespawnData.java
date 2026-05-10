package calebxzhou.rdi.mc.common2.mcp;

public record RMcpRespawnData(
        boolean wasDead,
        boolean respawned,
        boolean hardcore,
        float beforeHealth,
        float afterHealth,
        RMcpPosData before,
        RMcpPosData after
) {
}
