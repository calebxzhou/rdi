package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpItemPickupData(
        int requestedCount,
        int pickedCount,
        int failedCount,
        List<Result> results,
        RMcpInventoryData inventory
) {
    public record Result(
            String id,
            String code,
            boolean picked,
            int pickedCount,
            String beforeItem,
            String remainingItem,
            double motionSqr,
            double distance
    ) {
    }
}
