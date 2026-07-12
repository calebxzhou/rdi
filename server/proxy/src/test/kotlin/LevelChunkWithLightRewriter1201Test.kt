package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzau.util.netty.writeVarInt
import calebxzhou.rdi.common.model.McVersion
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelChunkWithLightRewriter1201Test {
    @Test
    fun rewritesChunkLightArraysToMaxValue() {
        val frame = createChunkFrame()
        val rewritten = LevelChunkWithLightRewriter1201.rewrite(frame, UnpooledByteBufAllocator.DEFAULT).getOrThrow()
        try {
            val declaredLength = rewritten.readVarInt()
            assertEquals(declaredLength, rewritten.readableBytes())
            rewritten.assertRewrittenChunkPacketData()
        } finally {
            rewritten.release()
            frame.release()
        }
    }

    @Test
    fun rewritesCompressedChunkLightArraysToMaxValue() {
        val packetData = createChunkPacketData()
        val compressedFrame = MinecraftCompression.encodePacketData(
            packetData,
            COMPRESSION_THRESHOLD,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        val decoded = MinecraftCompression.decodePacketData(
            compressedFrame,
            COMPRESSION_THRESHOLD,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        val rewrittenPacketData = LevelChunkWithLightRewriter1201.rewritePacketData(
            decoded,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        val rewrittenFrame = MinecraftCompression.encodePacketData(
            rewrittenPacketData,
            COMPRESSION_THRESHOLD,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        val decodedRewritten = MinecraftCompression.decodePacketData(
            rewrittenFrame,
            COMPRESSION_THRESHOLD,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        try {
            decodedRewritten.assertRewrittenChunkPacketData()
        } finally {
            decodedRewritten.release()
            rewrittenFrame.release()
            rewrittenPacketData.release()
            decoded.release()
            compressedFrame.release()
            packetData.release()
        }
    }

    @Test
    fun protocolVersionMapsToMcVersion() {
        assertEquals(McVersion.V201, McVersion.fromProtocolVer(763))
        assertEquals(McVersion.V122, McVersion.fromProtocolVer(340))
        assertEquals(McVersion.V071, McVersion.fromProtocolVer(5))
    }

    private fun createChunkFrame(): ByteBuf {
        val packetData = createChunkPacketData()
        return try {
            MinecraftCompression.encodePacketData(packetData, null, UnpooledByteBufAllocator.DEFAULT).getOrThrow()
        } finally {
            packetData.release()
        }
    }

    private fun createChunkPacketData(): ByteBuf = Unpooled.buffer().apply {
        writeVarInt(LevelChunkWithLightRewriter1201.PACKET_ID)
        writeInt(12)
        writeInt(-34)
        writeBytes(EMPTY_COMPOUND_NBT)
        writeVarInt(0)
        writeVarInt(1)
        writeByte(0x12)
        writeShort(64)
        writeVarInt(5)
        writeBytes(SIMPLE_COMPOUND_NBT)
        writeBitSetForTest(bitSetOf(1))
        writeBitSetForTest(bitSetOf(2))
        writeBitSetForTest(bitSetOf(3))
        writeBitSetForTest(bitSetOf(4))
        writeLightArrayForTest(0x00)
        writeLightArrayForTest(0x11)
    }

    private fun ByteBuf.assertRewrittenChunkPacketData() {
        assertEquals(LevelChunkWithLightRewriter1201.PACKET_ID, readVarInt())
        skipBytes(8)
        skipBytes(EMPTY_COMPOUND_NBT.size)
        assertEquals(0, readVarInt())
        assertEquals(1, readVarInt())
        skipBytes(3)
        assertEquals(5, readVarInt())
        skipBytes(SIMPLE_COMPOUND_NBT.size)

        assertEquals(bitSetOf(1, 3), readBitSetForTest())
        assertEquals(bitSetOf(2, 4), readBitSetForTest())
        assertTrue(readBitSetForTest().isEmpty)
        assertTrue(readBitSetForTest().isEmpty)
        assertMaxLightArrays(2)
        assertMaxLightArrays(2)
        assertEquals(0, readableBytes())
    }

    private fun ByteBuf.writeLightArrayForTest(value: Int) {
        writeVarInt(1)
        writeVarInt(2048)
        writeBytes(ByteArray(2048) { value.toByte() })
    }

    private fun ByteBuf.assertMaxLightArrays(expectedCount: Int) {
        assertEquals(expectedCount, readVarInt())
        repeat(expectedCount) {
            assertEquals(2048, readVarInt())
            repeat(2048) {
                assertEquals(0xFF, readUnsignedByte().toInt())
            }
        }
    }

    private fun ByteBuf.writeBitSetForTest(bitSet: java.util.BitSet) {
        val values = bitSet.toLongArray()
        writeVarInt(values.size)
        values.forEach { writeLong(it) }
    }

    private fun ByteBuf.readBitSetForTest(): java.util.BitSet {
        return java.util.BitSet.valueOf(LongArray(readVarInt()) { readLong() })
    }

    private fun bitSetOf(vararg bits: Int) = java.util.BitSet().apply {
        bits.forEach(::set)
    }

    private companion object {
        const val COMPRESSION_THRESHOLD = 256
        val EMPTY_COMPOUND_NBT = byteArrayOf(10, 0, 0, 0)
        val SIMPLE_COMPOUND_NBT = byteArrayOf(
            10, 0, 0,
            3, 0, 1, 'x'.code.toByte(), 0, 0, 0, 1,
            0
        )
    }
}
