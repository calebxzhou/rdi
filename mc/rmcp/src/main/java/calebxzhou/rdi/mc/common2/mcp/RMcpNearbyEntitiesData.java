package calebxzhou.rdi.mc.common2.mcp;

import java.util.List;

public record RMcpNearbyEntitiesData(
        String format,
        String dim,
        Center center,
        double radius,
        Summary summary,
        List<Entity> entities
) {
    public record Center(RMcpBlockPosData block, int chunkX, int chunkZ, int sectionY) {
    }

    public record EntityRef(String type, double distance) {
    }

    public record Summary(int total, int monsters, int animals, EntityRef nearestMonster, EntityRef nearestAnimal) {
    }

    public record Entity(
            String dim,
            String uuid,
            String type,
            String name,
            String category,
            RMcpPosData pos,
            double distance,
            Float health,
            Float maxHealth,
            boolean hostile,
            Boolean baby
    ) {
    }
}
