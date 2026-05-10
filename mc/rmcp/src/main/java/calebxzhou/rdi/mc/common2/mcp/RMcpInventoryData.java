package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpInventoryData(
        String dim,
        int selectedHotbarSlot,
        Item selectedItem,
        List<Item> hotbar,
        List<Item> items,
        List<Item> armor,
        List<Item> offhand,
        Summary summary
) {
    public record Item(
            String section,
            int slot,
            Integer hotbarSlot,
            String id,
            int count,
            String snbt
    ) {
    }

    public record ItemCount(String id, int count) {
    }

    public record Summary(
            int occupiedSlots,
            int emptySlots,
            int totalItems,
            List<ItemCount> topItems,
            boolean hasFood,
            boolean hasTool,
            boolean hasWeapon,
            boolean hasBlock
    ) {
    }
}
