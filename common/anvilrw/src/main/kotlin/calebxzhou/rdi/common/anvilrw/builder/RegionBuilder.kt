package calebxzhou.rdi.common.anvilrw.builder

import calebxzhou.rdi.common.anvilrw.AnvilFactory
import calebxzhou.rdi.common.anvilrw.core.Chunk
import calebxzhou.rdi.common.anvilrw.core.Location
import calebxzhou.rdi.common.anvilrw.core.Region
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION_SIDE
import java.io.File

class RegionBuilder private constructor() {
    private val chunks = mutableMapOf<Int, Chunk>()
    private var regionX = 0
    private var regionZ = 0
    private var validateChunks = true

    companion object {
        fun create(): RegionBuilder = RegionBuilder()

        fun fromFile(file: File): RegionBuilder {
            val builder = RegionBuilder()
            AnvilFactory.createReader(file).use { reader ->
                val region = reader.readRegion()
                builder.regionX = region.x
                builder.regionZ = region.z
                for (chunk in region.chunks) {
                    if (!chunk.isEmpty) {
                        builder.chunks[chunk.index] = chunk
                    }
                }
            }
            return builder
        }

        fun fromFile(filePath: String): RegionBuilder = fromFile(File(filePath))
    }

    fun withCoordinates(regionX: Int, regionZ: Int) = apply {
        this.regionX = regionX
        this.regionZ = regionZ
    }

    fun addChunk(chunk: Chunk) = apply {
        if (validateChunks) validateChunkCoordinates(chunk)
        chunks[chunk.index] = chunk
    }

    fun addChunks(chunks: List<Chunk>) = apply {
        for (chunk in chunks) addChunk(chunk)
    }

    fun addEmptyChunk(chunkX: Int, chunkZ: Int) = apply {
        val index = calculateChunkIndex(chunkX, chunkZ)
        val location = Location.createEmpty()
        val emptyChunk = Chunk(index, location, 0, ByteArray(0))
        addChunk(emptyChunk)
    }

    fun removeChunk(chunkX: Int, chunkZ: Int) = apply {
        val index = calculateChunkIndex(chunkX, chunkZ)
        chunks.remove(index)
    }

    fun removeChunk(index: Int) = apply {
        require(index in 0 until CHUNKS_PER_REGION) {
            "Chunk index must be between 0 and ${CHUNKS_PER_REGION - 1}, got: $index"
        }
        chunks.remove(index)
    }

    fun validateChunks(validate: Boolean) = apply { validateChunks = validate }
    fun clearChunks() = apply { chunks.clear() }
    val chunkCount: Int get() = chunks.size
    val hasChunks: Boolean get() = chunks.isNotEmpty()

    fun build(): Region {
        validateBuilder()
        val allChunks = (0 until CHUNKS_PER_REGION).map { i ->
            chunks[i] ?: Chunk(i, Location.createEmpty(), 0, ByteArray(0))
        }
        return Region(regionX, regionZ, allChunks)
    }

    private fun calculateChunkIndex(chunkX: Int, chunkZ: Int): Int =
        Math.floorMod(chunkZ, CHUNKS_PER_REGION_SIDE) * CHUNKS_PER_REGION_SIDE +
            Math.floorMod(chunkX, CHUNKS_PER_REGION_SIDE)

    private fun validateChunkCoordinates(chunk: Chunk) {
        val expectedRegionX = Math.floorDiv(chunk.x, CHUNKS_PER_REGION_SIDE)
        val expectedRegionZ = Math.floorDiv(chunk.z, CHUNKS_PER_REGION_SIDE)
        require(expectedRegionX == regionX && expectedRegionZ == regionZ) {
            "Chunk at (${chunk.x}, ${chunk.z}) does not belong to region ($regionX, $regionZ). Expected region: ($expectedRegionX, $expectedRegionZ)"
        }
    }

    private fun validateBuilder() {
        check(chunks.size <= CHUNKS_PER_REGION) {
            "Too many chunks: ${chunks.size} (maximum $CHUNKS_PER_REGION)"
        }
    }
}
