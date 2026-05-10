package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpContainerData(
        String dim,
        RMcpBlockPosData pos,
        String side,
        int slots,
        List<Slot> items
) {
    public record Slot(
            int slot,
            String id,
            int count,
            int limit,
            boolean canInsert,
            String snbt
    ) {
    }
}
