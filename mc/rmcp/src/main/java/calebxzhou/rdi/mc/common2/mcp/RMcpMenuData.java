package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpMenuData(
        String dim,
        int containerId,
        String menuClass,
        boolean inventoryMenu,
        Slot carriedItem,
        int slotCount,
        List<Slot> slots
) {
    public record Slot(
            int slot,
            boolean empty,
            boolean mayPickup,
            String id,
            int count,
            int limit,
            String snbt
    ) {
    }
}
