package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpSituationData(
        RMcpPlayerData player,
        Environment environment,
        Inventory inventory,
        Nearby nearby
) {
    public record Environment(
            String dim,
            String biome,
            long gameTime,
            long dayTime,
            long timeOfDay,
            String timeBucket,
            boolean raining,
            boolean thundering,
            String difficulty,
            Light light,
            boolean canSeeSky,
            boolean inWater,
            boolean underWater,
            boolean onGround
    ) {
    }

    public record Light(int block, int sky, int raw) {
    }

    public record Inventory(
            int selectedHotbarSlot,
            RMcpInventoryData.Item selectedItem,
            List<RMcpInventoryData.Item> armor,
            RMcpInventoryData.Item offhand,
            RMcpInventoryData.Summary summary
    ) {
    }

    public record Nearby(
            Range range,
            RMcpNearbyEntitiesData.Summary entities,
            List<RMcpNearbyEntitiesData.Entity> entitySamples,
            RMcpNearbyResourcesData.Features resources,
            List<RMcpNearbyResourcesData.Resource> resourceSamples
    ) {
    }

    public record Range(double entityRadius, int resourceChunkRadius, int resourceSectionRadius) {
    }
}
