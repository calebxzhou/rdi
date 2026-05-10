package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpPlaceDiscreteRequest(
        String blockId,
        List<Target> targets,
        boolean dryRun
) {
    public record Target(
            RMcpBlockPosData pos,
            Map<String, String> state
    ) {
    }
}
