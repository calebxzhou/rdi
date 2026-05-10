package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;
import java.util.Map;

public record RMcpPlacePaletteRequest(
        Map<String, Entry> palette,
        List<Target> targets,
        boolean dryRun
) {
    public record Entry(
            String blockId,
            Map<String, String> state
    ) {
    }

    public record Target(
            RMcpBlockPosData pos,
            String key
    ) {
    }
}
