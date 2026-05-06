package calebxzhou.rdi.mc.common2.mcp;

public record RMcpBlockEntityData(
        String dim,
        RMcpBlockPosData pos,
        String blockId,
        String blockState,
        String type,
        String runtimeClass,
        String snbt
) {
}
