package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpModData(
        String id,
        String name,
        String version,
        String description,
        List<Dependency> dependencies
) {
    public record Dependency(
            String id,
            String versionRange,
            String type,
            String ordering,
            String side
    ) {
    }
}
