package calebxzhou.rdi.mc.common2.mcp;

public record RMcpPosData(String dim, double x, double y, double z, float yaw, float pitch, int chunkX, int chunkZ, int sectionY) {
    public RMcpPosData(String dim, double x, double y, double z, float yaw, float pitch) {
        this(
                dim,
                x,
                y,
                z,
                yaw,
                pitch,
                Math.floorDiv((int) Math.floor(x), 16),
                Math.floorDiv((int) Math.floor(z), 16),
                Math.floorDiv((int) Math.floor(y), 16)
        );
    }
}
