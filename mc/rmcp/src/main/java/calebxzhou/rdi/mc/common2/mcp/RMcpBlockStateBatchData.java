package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpBlockStateBatchData(String format, String dim, int requestedCount, List<RMcpBlockStateEntryData> blocks) {
}
