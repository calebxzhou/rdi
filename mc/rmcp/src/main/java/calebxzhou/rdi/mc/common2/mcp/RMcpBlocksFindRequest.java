package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlocksFindRequest(
        String id,
        List<String> ids,
        Integer chunkRadius,
        Integer sectionRadius,
        Integer limit,
        Boolean includeState
) {
}
