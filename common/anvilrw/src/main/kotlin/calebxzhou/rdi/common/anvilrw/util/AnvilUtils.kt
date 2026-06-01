package calebxzhou.rdi.common.anvilrw.util
import net.benwoodworth.knbt.*

import calebxzhou.rdi.common.anvilrw.core.Location
import calebxzhou.rdi.common.anvilrw.format.FileFormat
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.BLOCKS_PER_CHUNK_SIDE
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION_SIDE
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.SECTOR_SIZE_BYTES
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AnvilUtils {
    const val SECTOR_SIZE = 4096

    fun readInt(data: ByteArray, order: ByteOrder): Int {
        val buffer = ByteBuffer.allocate(4).order(order)
        buffer.position(4 - data.size)
        buffer.put(data)
        buffer.rewind()
        return buffer.int
    }

    fun padToSectorSize(data: ByteArray, sectorSize: Int = SECTOR_SIZE): ByteArray {
        require(sectorSize > 0) { "Sector size must be positive, got: $sectorSize" }
        val remainder = data.size % sectorSize
        if (remainder == 0) return data
        val neededPadding = sectorSize - remainder
        val paddedData = ByteArray(data.size + neededPadding)
        System.arraycopy(data, 0, paddedData, 0, data.size)
        return paddedData
    }

    fun calculateSectorCount(dataSize: Int): Int {
        if (dataSize <= 0) return 0
        return Math.ceil(dataSize.toDouble() / SECTOR_SIZE).toInt()
    }

    fun isSectorAligned(offset: Long): Boolean = offset % SECTOR_SIZE == 0L

    fun blockToChunk(blockX: Int, blockZ: Int): IntArray =
        intArrayOf(
            Math.floorDiv(blockX, BLOCKS_PER_CHUNK_SIDE),
            Math.floorDiv(blockZ, BLOCKS_PER_CHUNK_SIDE)
        )

    fun chunkToRegion(chunkX: Int, chunkZ: Int): IntArray =
        intArrayOf(
            Math.floorDiv(chunkX, CHUNKS_PER_REGION_SIDE),
            Math.floorDiv(chunkZ, CHUNKS_PER_REGION_SIDE)
        )

    fun blockToRegion(blockX: Int, blockZ: Int): IntArray {
        val chunkCoords = blockToChunk(blockX, blockZ)
        return chunkToRegion(chunkCoords[0], chunkCoords[1])
    }

    fun generateRegionFilename(regionX: Int, regionZ: Int): String =
        "r.$regionX.$regionZ.${FileFormat.ANVIL.extension}"

    fun parseRegionFilename(filename: String): IntArray {
        require(filename.isNotBlank()) { "Filename cannot be null or empty" }
        val parts = filename.split(".")
        require(parts.size == 4 && parts[0] == "r" && parts[3] == FileFormat.ANVIL.extension) {
            "Invalid region filename format. Expected: r.x.z.mca, got: $filename"
        }
        try {
            return intArrayOf(parts[1].toInt(), parts[2].toInt())
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException(
                "Invalid coordinates in filename: $filename. Coordinates must be integers.", e
            )
        }
    }

    fun isValidRegionFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false
        return try {
            parseRegionFilename(file.name)
            file.length() >= 8192
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun chunkCoordinatesToIndex(chunkX: Int, chunkZ: Int): Int =
        Math.floorMod(chunkZ, CHUNKS_PER_REGION_SIDE) * CHUNKS_PER_REGION_SIDE +
            Math.floorMod(chunkX, CHUNKS_PER_REGION_SIDE)

    fun calculateChunkCoordinates(regionX: Int, regionZ: Int, chunkIndex: Int): IntArray {
        require(chunkIndex in 0 until CHUNKS_PER_REGION) {
            "Chunk index must be between 0 and ${CHUNKS_PER_REGION - 1}, got: $chunkIndex"
        }
        val localX = chunkIndex % CHUNKS_PER_REGION_SIDE
        val localZ = chunkIndex / CHUNKS_PER_REGION_SIDE
        return intArrayOf(
            regionX * CHUNKS_PER_REGION_SIDE + localX,
            regionZ * CHUNKS_PER_REGION_SIDE + localZ
        )
    }

    fun createLocation(offset: Int, sectorCount: Int): Location {
        require(offset in 0..16777215) { "Offset must be between 0 and 16777215, got: $offset" }
        require(sectorCount in 0..255) { "Sector count must be between 0 and 255, got: $sectorCount" }
        val locationBytes = byteArrayOf(
            ((offset shr 16) and 0xFF).toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte(),
            sectorCount.toByte()
        )
        return Location(locationBytes)
    }

    fun isValidChunkPlacement(offset: Int, sectorCount: Int, fileSize: Long): Boolean {
        if (offset < 2) return false
        if (sectorCount <= 0 || sectorCount > 255) return false
        val chunkStart = offset.toLong() * SECTOR_SIZE
        val chunkEnd = chunkStart + sectorCount.toLong() * SECTOR_SIZE
        return chunkEnd <= fileSize
    }
    fun unwrapRoot(root: NbtCompound): NbtCompound {
        if (root.size == 1 && "" in root) {
            val inner = root[""]
            if (inner is NbtCompound) return inner
        }
        return root
    }

    fun wrapRoot(root: NbtCompound): NbtCompound {
        if (root.size == 1 && "" in root && root[""] is NbtCompound) return root
        return buildNbtCompound {
            put("", root)
        }
    }
}
