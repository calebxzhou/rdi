package calebxzhou.rdi.common.anvilrw.builder

import calebxzhou.rdi.common.anvilrw.core.Chunk
import calebxzhou.rdi.common.anvilrw.core.ChunkPayload
import calebxzhou.rdi.common.anvilrw.core.Location
import calebxzhou.rdi.common.anvilrw.util.AnvilConstants.CHUNKS_PER_REGION
import calebxzhou.rdi.common.anvilrw.util.AnvilUtils
import net.benwoodworth.knbt.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ChunkBuilder private constructor() {
    private var chunkX = 0
    private var chunkZ = 0
    private var xExplicitlySet = false
    private var zExplicitlySet = false
    private var dataVersion = 4325 // Default to 1.21.5
    private var timestamp = (System.currentTimeMillis() / 1000).toInt()
    private var nbtData: NbtCompound? = null
    private var compressionType: Byte = 2 // Default to Zlib
    private var index = -1
    private var location: Location? = null
    private var validateCoordinates = true

    companion object {
        fun create(): ChunkBuilder = ChunkBuilder()

        fun fromChunk(chunk: Chunk): ChunkBuilder {
            val builder = ChunkBuilder()
            builder.chunkX = chunk.x
            builder.chunkZ = chunk.z
            builder.xExplicitlySet = true
            builder.zExplicitlySet = true
            builder.dataVersion = chunk.dataVersion
            builder.timestamp = chunk.timestamp
            builder.index = chunk.index
            builder.location = chunk.location
            builder.compressionType = chunk.payload.compressionType

            val existingNbt = chunk.getNbtData()
            if (existingNbt != null) {
                builder.nbtData = existingNbt
            } else {
                println("Warning: Source chunk has no NBT data, will create minimal chunk structure")
            }
            return builder
        }
    }

    fun withCoordinates(chunkX: Int, chunkZ: Int) = apply {
        this.chunkX = chunkX
        this.chunkZ = chunkZ
        xExplicitlySet = true
        zExplicitlySet = true
    }

    fun withX(x: Int) = apply { chunkX = x; xExplicitlySet = true }
    fun withZ(z: Int) = apply { chunkZ = z; zExplicitlySet = true }
    fun withDataVersion(dataVersion: Int) = apply { this.dataVersion = dataVersion }
    fun withTimestamp(timestamp: Int) = apply { this.timestamp = timestamp }
    fun withCurrentTimestamp() = apply { timestamp = (System.currentTimeMillis() / 1000).toInt() }
    fun withNbtData(nbtData: NbtCompound) = apply { this.nbtData = nbtData }
    fun withZlibCompression() = apply { compressionType = 2 }
    fun withGZipCompression() = apply { compressionType = 1 }
    fun withUncompressed() = apply { compressionType = 3 }

    fun withIndex(index: Int) = apply {
        require(index in 0 until CHUNKS_PER_REGION) {
            "Chunk index must be between 0 and ${CHUNKS_PER_REGION - 1}, got: $index"
        }
        this.index = index
    }

    fun withLocation(location: Location) = apply { this.location = location }
    fun withEmptyLocation() = apply { location = Location.createEmpty() }
    fun validateCoordinates(validate: Boolean) = apply { validateCoordinates = validate }
    fun asEmptyChunk() = apply { nbtData = null }

    fun build(): Chunk {
        validateBuilder()

        if (index == -1) {
            index = AnvilUtils.chunkCoordinatesToIndex(chunkX, chunkZ)
        }
        val expectedIndex = AnvilUtils.chunkCoordinatesToIndex(chunkX, chunkZ)
        if (index != expectedIndex) {
            index = expectedIndex
        }
        if (location == null) {
            location = Location.createEmpty()
        }

        val currentNbt = nbtData
        if (currentNbt == null) {
            return Chunk(index, location!!, timestamp, ByteArray(0))
        } else {
            val updatedNbt = updateNbtCoordinates(currentNbt)
            val nbtPayload = createNbtPayload(updatedNbt)
            val sectorCount = AnvilUtils.calculateSectorCount(nbtPayload.size)
            location = AnvilUtils.createLocation(0, sectorCount)
            return Chunk(index, location!!, timestamp, nbtPayload)
        }
    }

    private fun createNbtPayload(nbtData: NbtCompound): ByteArray {
        val nbt = Nbt {
            variant = NbtVariant.Java
            compression = NbtCompression.None
        }
        val wrapped = AnvilUtils.wrapRoot(nbtData)
        val nbtBytes = nbt.encodeToByteArray(NbtCompound.serializer(), wrapped)

        val tempPayload = ChunkPayload(ByteArray(0))
        val compressedData = tempPayload.compressData(nbtBytes, compressionType)

        val buffer = ByteBuffer.allocate(5 + compressedData.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(compressedData.size + 1)
        buffer.put(compressionType)
        buffer.put(compressedData)
        return buffer.array()
    }

    private fun updateNbtCoordinates(nbtData: NbtCompound): NbtCompound {
        var finalX = chunkX
        val root = AnvilUtils.unwrapRoot(nbtData)
        var finalZ = chunkZ

        if (!xExplicitlySet && "xPos" in root) {
            finalX = (root["xPos"] as NbtInt).value
            chunkX = finalX
        }
        if (!zExplicitlySet && "zPos" in root) {
            finalZ = (root["zPos"] as NbtInt).value
            chunkZ = finalZ
        }

        return buildNbtCompound {
            // Copy all existing entries
            for ((key, value) in root) {
                if (key != "xPos" && key != "zPos") {
                    put(key, value)
                }
            }
            put("xPos", finalX)
            put("zPos", finalZ)
        }
    }

    private fun validateBuilder() {
        if (validateCoordinates) {
            check(Math.abs(chunkX) <= 1875000 && Math.abs(chunkZ) <= 1875000) {
                "Chunk coordinates are too large: ($chunkX, $chunkZ)"
            }
        }
        if (dataVersion <= 0) {
            println("Warning: Invalid data version ($dataVersion), setting to default 4325")
            dataVersion = 4325
        }
        check(timestamp >= 0) { "Timestamp cannot be negative, got: $timestamp" }
    }
}
