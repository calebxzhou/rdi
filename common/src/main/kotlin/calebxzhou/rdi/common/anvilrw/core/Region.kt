package calebxzhou.rdi.common.anvilrw.core

import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.BLOCKS_PER_CHUNK_SIDE
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION_SIDE
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.SECTOR_SIZE_BYTES
import calebxzhou.rdi.common.anvilrw.util.AnvilUtils
import java.io.RandomAccessFile
import java.nio.ByteOrder

class Region {
    val x: Int
    val z: Int
    val chunks: MutableList<Chunk>

    constructor(x: Int, z: Int, anvilFile: RandomAccessFile) {
        this.x = x
        this.z = z
        this.chunks = readAllChunks(anvilFile)
    }

    constructor(x: Int, z: Int, chunks: List<Chunk>) {
        require(chunks.size == CHUNKS_PER_REGION) {
            "Chunks list must contain exactly $CHUNKS_PER_REGION chunks, got: ${chunks.size}"
        }
        this.x = x
        this.z = z
        this.chunks = chunks.toMutableList()
    }

    fun replaceChunk(chunk: Chunk) {
        val targetIndex = AnvilUtils.chunkCoordinatesToIndex(chunk.x, chunk.z)
        require(containsChunk(chunk.x, chunk.z)) {
            "Chunk at (${chunk.x}, ${chunk.z}) does not belong to region ($x, $z)"
        }
        chunks[targetIndex] = chunk
    }

    fun getChunk(chunkX: Int, chunkZ: Int): Chunk? {
        if (!containsChunk(chunkX, chunkZ)) return null
        val index = AnvilUtils.chunkCoordinatesToIndex(chunkX, chunkZ)
        if (index < 0 || index >= chunks.size) return null
        val chunk = chunks[index]
        if (!chunk.isEmpty) {
            if (chunk.x == chunkX && chunk.z == chunkZ) {
                return chunk
            } else {
                System.err.printf(
                    "WARNING: Retrieved chunk position [%d, %d] does not match requested [%d, %d] at index %d%n",
                    chunk.x, chunk.z, chunkX, chunkZ, index
                )
                return null
            }
        }
        return chunk
    }

    fun getChunksWithOwnables(): List<Chunk> =
        chunks.filter { it.hasOwnableEntities() }

    fun chunkInRegion(chunk: Chunk): Boolean {
        val chunkRegionCoordinates = chunk.chunkToRegionCoordinate()
        return chunkRegionCoordinates[0] == x && chunkRegionCoordinates[1] == z
    }

    fun containsChunk(chunkX: Int, chunkZ: Int): Boolean {
        val regionStartX = x * CHUNKS_PER_REGION_SIDE
        val regionEndX = regionStartX + CHUNKS_PER_REGION_SIDE - 1
        val regionStartZ = z * CHUNKS_PER_REGION_SIDE
        val regionEndZ = regionStartZ + CHUNKS_PER_REGION_SIDE - 1
        return chunkX in regionStartX..regionEndX && chunkZ in regionStartZ..regionEndZ
    }

    fun getStartingBlockCoordinates(): IntArray {
        val regionX = x * (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE)
        val regionZ = z * (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE)
        return intArrayOf(regionX, regionZ)
    }

    fun getBlockCoordinateRange(): IntArray {
        val startX = x * (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE)
        val endX = startX + (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE - 1)
        val startZ = z * (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE)
        val endZ = startZ + (CHUNKS_PER_REGION_SIDE * BLOCKS_PER_CHUNK_SIDE - 1)
        return intArrayOf(startX, startZ, endX, endZ)
    }

    private fun readAllChunks(raf: RandomAccessFile): MutableList<Chunk> {
        val locationCount = SECTOR_SIZE_BYTES / 4
        val locations = Array(locationCount) { i ->
            raf.seek(i * 4L)
            val byteBuffer = ByteArray(4)
            raf.read(byteBuffer)
            Location(byteBuffer)
        }
        val timestamps = IntArray(locationCount) { i ->
            raf.seek(SECTOR_SIZE_BYTES + i * 4L)
            val byteBuffer = ByteArray(4)
            raf.read(byteBuffer)
            AnvilUtils.readInt(byteBuffer, ByteOrder.BIG_ENDIAN)
        }
        val chunks = MutableList(locationCount) { i ->
            Chunk(i, Location.createEmpty(), 0, ByteArray(0))
        }
        val nonEmptyChunkMeta = (0 until locationCount).mapNotNull { i ->
            val location = locations[i]
            if (location.offset == 0 || location.sectorCount == 0) {
                chunks[i] = Chunk(i, location, timestamps[i], ByteArray(0))
                null
            } else {
                Triple(i, location, timestamps[i])
            }
        }.sortedBy { (_, location, _) -> location.offset }

        for ((i, location, timestamp) in nonEmptyChunkMeta) {
            raf.seek(location.offset * SECTOR_SIZE_BYTES.toLong())
            val chunkData = ByteArray(location.sectorCount * SECTOR_SIZE_BYTES)
            raf.read(chunkData)
            chunks[i] = Chunk(i, location, timestamp, chunkData)
        }
        return chunks
    }
}
