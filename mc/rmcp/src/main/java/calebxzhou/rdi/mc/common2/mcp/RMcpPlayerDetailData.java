package calebxzhou.rdi.mc.common2.mcp;

import java.util.Map;

public record RMcpPlayerDetailData(
        RMcpPlayerData brief,
        RMcpEntityDetailData entity,
        Map<String, Object> profile,
        Map<String, Object> player,
        Map<String, Object> inventory
) {
}
