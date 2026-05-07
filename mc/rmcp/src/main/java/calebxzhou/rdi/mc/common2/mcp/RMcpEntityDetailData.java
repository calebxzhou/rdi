package calebxzhou.rdi.mc.common2.mcp;

import java.util.Map;

public record RMcpEntityDetailData(
        String dim,
        String uuid,
        String type,
        String name,
        RMcpPosData pos,
        Map<String, Object> runtime,
        Map<String, Object> nbt,
        String snbt
) {
}
