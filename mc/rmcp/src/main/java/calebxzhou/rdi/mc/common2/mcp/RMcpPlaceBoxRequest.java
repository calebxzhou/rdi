package calebxzhou.rdi.mc.common2.mcp;

import java.util.Map;

public record RMcpPlaceBoxRequest(
        String blockId,
        RMcpBlockPosData startPos,
        RMcpBlockPosData endOffset,
        Map<String, String> state,
        boolean dryRun
) {
}
