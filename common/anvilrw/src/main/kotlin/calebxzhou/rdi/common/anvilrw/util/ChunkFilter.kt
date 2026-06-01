package calebxzhou.rdi.common.anvilrw.util

import calebxzhou.rdi.common.anvilrw.core.Chunk

object ChunkFilter {
    fun filterByDataVersion(chunks: List<Chunk>, minVersion: Int, maxVersion: Int): List<Chunk> =
        chunks.filter { !it.isEmpty && it.dataVersion in minVersion..maxVersion }

    fun filterByCoordinateRange(
        chunks: List<Chunk>, minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int
    ): List<Chunk> =
        chunks.filter { !it.isEmpty && it.x in minChunkX..maxChunkX && it.z in minChunkZ..maxChunkZ }

    fun filterByBlockCoordinates(
        chunks: List<Chunk>, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int
    ): List<Chunk> {
        val minChunk = AnvilUtils.blockToChunk(minBlockX, minBlockZ)
        val maxChunk = AnvilUtils.blockToChunk(maxBlockX, maxBlockZ)
        return filterByCoordinateRange(chunks, minChunk[0], minChunk[1], maxChunk[0], maxChunk[1])
    }

    fun filterWithOwnables(chunks: List<Chunk>): List<Chunk> =
        chunks.filter { !it.isEmpty }.filter {
            try {
                it.hasOwnableEntities()
            } catch (e: Exception) {
                System.err.println("Warning: Failed to check ownable entities for chunk at (${it.x}, ${it.z}): ${e.message}")
                false
            }
        }

    fun filterNonEmpty(chunks: List<Chunk>): List<Chunk> =
        chunks.filter { !it.isEmpty }
}
