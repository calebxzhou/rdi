package calebxzhou.rdi.mc.proxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class ZstdFrameCodecTest {
    @Test
    fun `round trips compressed frame`() {
        val encoder = EmbeddedChannel(ZstdFrameEncoder(level = 3, threshold = 1))
        val decoder = EmbeddedChannel(ZstdFrameDecoder(maxFrameSize = 1024))
        val expected = "shared-proxy-codec".repeat(8).toByteArray()

        try {
            encoder.writeOutbound(Unpooled.wrappedBuffer(expected))
            val encoded = encoder.readOutbound<ByteBuf>()
            decoder.writeInbound(encoded)
            val decoded = decoder.readInbound<ByteBuf>()
            try {
                val actual = ByteArray(decoded.readableBytes())
                decoded.readBytes(actual)
                assertContentEquals(expected, actual)
            } finally {
                decoded.release()
            }
        } finally {
            encoder.finishAndReleaseAll()
            decoder.finishAndReleaseAll()
        }
    }

    @Test
    fun `auto decoder passes through plain minecraft traffic`() {
        val channel = EmbeddedChannel(ZstdFrameAutoDecoder(maxFrameSize = 1024))
        val expected = byteArrayOf(3, 1, 2, 3)
        try {
            channel.writeInbound(Unpooled.wrappedBuffer(expected))
            val decoded = channel.readInbound<ByteBuf>()
            try {
                val actual = ByteArray(decoded.readableBytes())
                decoded.readBytes(actual)
                assertContentEquals(expected, actual)
                assertFalse(channel.isZstdFrameEnabled())
            } finally {
                decoded.release()
            }
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
