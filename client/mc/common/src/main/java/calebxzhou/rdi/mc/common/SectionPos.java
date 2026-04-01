package calebxzhou.rdi.mc.common;

/**
 * calebxzhou @ 2026-03-31 12:59
 */
public class SectionPos {
    private int chunkX;
    private int index;
    private int chunkZ;

    public SectionPos(int chunkX, int index, int chunkZ) {
        this.chunkX = chunkX;
        this.index = index;
        this.chunkZ = chunkZ;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }
}
