package calebxzhou.rdi.mc.common2.mcp;

public record RMcpPlayerData(
        String dim,
        String uuid,
        String name,
        RMcpPosData pos,
        Float health,
        Float maxHealth,
        Integer food,
        String gameMode,
        Integer latency
) {
}
