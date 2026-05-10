package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpSignTextReadData(
        RMcpBlockPosData pos,
        boolean waxed,
        Side front,
        Side back
) {
    public record Side(
            String color,
            boolean glowing,
            List<String> lines,
            boolean hasText
    ) {
    }
}
