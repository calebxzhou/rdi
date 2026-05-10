package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpSignTextData(
        boolean dryRun,
        RMcpBlockPosData pos,
        String side,
        boolean changed,
        List<String> lines
) {
}
