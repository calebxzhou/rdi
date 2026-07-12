package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzau.util.netty.writeVarInt
import io.netty.buffer.Unpooled
import io.netty.buffer.UnpooledByteBufAllocator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MinecraftCompressionTest {
    @Test
    fun peeksCompressedPacketId() {
        val packetData = Unpooled.buffer().apply {
            writeVarInt(0x24)
            writeBytes(ByteArray(2048) { 0x7F.toByte() })
        }
        val frame = MinecraftCompression.encodePacketData(
            packetData,
            16,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        try {
            assertEquals(0x24, MinecraftCompression.peekPacketId(frame, 16).getOrThrow())
        } finally {
            frame.release()
            packetData.release()
        }
    }

    @Test
    fun keepsPacketBelowThresholdUncompressed() {
        val packetData = Unpooled.buffer().apply {
            writeVarInt(0x02)
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val frame = MinecraftCompression.encodePacketData(
            packetData,
            256,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        val decoded = MinecraftCompression.decodePacketData(
            frame,
            256,
            UnpooledByteBufAllocator.DEFAULT
        ).getOrThrow()
        try {
            val parser = frame.duplicate()
            assertEquals(parser.readVarInt(), parser.readableBytes())
            assertEquals(0, parser.readVarInt())

            val expected = ByteArray(packetData.readableBytes())
            val actual = ByteArray(decoded.readableBytes())
            packetData.getBytes(packetData.readerIndex(), expected)
            decoded.getBytes(decoded.readerIndex(), actual)
            assertContentEquals(expected, actual)
        } finally {
            decoded.release()
            frame.release()
            packetData.release()
        }
    }
}
