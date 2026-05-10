package calebxzhou.rdi.mc.common2.mcp;

public record RMcpSignTextRequest(
        RMcpBlockPosData pos,
        String side,
        String text,
        boolean dryRun
) {
}
