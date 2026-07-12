package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzau.util.netty.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import net.benwoodworth.knbt.Nbt
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtCompression
import net.benwoodworth.knbt.NbtVariant
import net.benwoodworth.knbt.decodeFromStream
import java.io.InputStream
import java.util.BitSet

internal object LevelChunkWithLightRewriter1201 {
    const val PACKET_ID = 0x24

    private const val MAX_CHUNK_DATA_SIZE = 2 * 1024 * 1024
    private const val MAX_LIGHT_MASK_LONGS = 256
    private const val LIGHT_SECTION_SIZE = 2048
    private val maxLightSection = ByteArray(LIGHT_SECTION_SIZE) { 0xFF.toByte() }
    private val nbt = Nbt {
        variant = NbtVariant.Java
        compression = NbtCompression.None
    }

    fun rewrite(frame: ByteBuf, allocator: ByteBufAllocator): Result<ByteBuf> = runCatching {
        val packetData = MinecraftCompression.decodePacketData(frame, null, allocator).getOrThrow()
        try {
            val rewritten = rewritePacketData(packetData, allocator).getOrThrow()
            try {
                MinecraftCompression.encodePacketData(rewritten, null, allocator).getOrThrow()
            } finally {
                rewritten.release()
            }
        } finally {
            packetData.release()
        }
    }

    fun rewritePacketData(packetData: ByteBuf, allocator: ByteBufAllocator): Result<ByteBuf> = runCatching {
        val input = packetData.duplicate()
        val packetDataStart = input.readerIndex()
        require(input.readVarInt() == PACKET_ID) { "Unexpected packet id" }
        input.skipBytes(8)

        input.skipNbt()
        val chunkDataSize = input.readVarInt()
        require(chunkDataSize in 0..MAX_CHUNK_DATA_SIZE) { "Invalid chunk data size: $chunkDataSize" }
        input.skipBytes(chunkDataSize)

        val blockEntityCount = input.readVarInt()
        require(blockEntityCount in 0..65536) { "Invalid block entity count: $blockEntityCount" }
        repeat(blockEntityCount) {
            input.skipBytes(3)
            input.readVarInt()
            input.skipNbt()
        }

        val lightDataStart = input.readerIndex()
        val skyMask = input.readBitSet()
        val blockMask = input.readBitSet()
        val emptySkyMask = input.readBitSet()
        val emptyBlockMask = input.readBitSet()
        input.skipLightArrays(skyMask.cardinality())
        input.skipLightArrays(blockMask.cardinality())
        require(!input.isReadable) { "Unexpected trailing chunk packet data: ${input.readableBytes()} bytes" }

        val skySections = (skyMask.clone() as BitSet).apply { or(emptySkyMask) }
        val blockSections = (blockMask.clone() as BitSet).apply { or(emptyBlockMask) }
        val rewritten = allocator.buffer()
        try {
            rewritten.writeBytes(packetData, packetDataStart, lightDataStart - packetDataStart)
            rewritten.writeBitSet(skySections)
            rewritten.writeBitSet(blockSections)
            rewritten.writeBitSet(BitSet())
            rewritten.writeBitSet(BitSet())
            rewritten.writeMaxLightArrays(skySections.cardinality())
            rewritten.writeMaxLightArrays(blockSections.cardinality())
            rewritten
        } catch (throwable: Throwable) {
            rewritten.release()
            throw throwable
        }
    }

    private fun ByteBuf.skipNbt() {
        require(isReadable) { "Missing NBT" }
        if (getUnsignedByte(readerIndex()).toInt() == 0) {
            skipBytes(1)
            return
        }
        nbt.decodeFromStream(NbtCompound.serializer(), SingleByteByteBufInputStream(this))
    }

    private fun ByteBuf.readBitSet(): BitSet {
        val longCount = readVarInt()
        require(longCount in 0..MAX_LIGHT_MASK_LONGS) { "Invalid light mask length: $longCount" }
        return BitSet.valueOf(LongArray(longCount) { readLong() })
    }

    private fun ByteBuf.skipLightArrays(expectedCount: Int) {
        val count = readVarInt()
        require(count == expectedCount) {
            "Light array count mismatch: expected=$expectedCount actual=$count"
        }
        repeat(count) {
            val size = readVarInt()
            require(size in 0..LIGHT_SECTION_SIZE) { "Invalid light array size: $size" }
            skipBytes(size)
        }
    }

    private fun ByteBuf.writeBitSet(bitSet: BitSet) {
        val values = bitSet.toLongArray()
        writeVarInt(values.size)
        values.forEach { writeLong(it) }
    }

    private fun ByteBuf.writeMaxLightArrays(count: Int) {
        writeVarInt(count)
        repeat(count) {
            writeVarInt(LIGHT_SECTION_SIZE)
            writeBytes(maxLightSection)
        }
    }
}

private class SingleByteByteBufInputStream(
    private val buffer: ByteBuf
) : InputStream() {
    override fun read(): Int = if (buffer.isReadable) buffer.readUnsignedByte().toInt() else -1

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val value = read()
        if (value == -1) return -1
        bytes[offset] = value.toByte()
        return 1
    }

    override fun close() = Unit
}
