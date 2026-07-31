package calebxzhou.rdi.mc.proxy

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftFrameDecoderTest {
    @Test
    fun `waits for complete frame`() {
        val channel = EmbeddedChannel(MinecraftFrameDecoder())
        try {
            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(3, 1))))
            assertNull(channel.readInbound<ByteBuf>())
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(byteArrayOf(2, 3))))

            val frame = channel.readInbound<ByteBuf>()
            try {
                val bytes = ByteArray(frame.readableBytes())
                frame.readBytes(bytes)
                assertContentEquals(byteArrayOf(3, 1, 2, 3), bytes)
            } finally {
                frame.release()
            }
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
