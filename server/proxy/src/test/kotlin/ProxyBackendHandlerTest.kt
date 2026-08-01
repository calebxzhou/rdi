package calebxzhou.rdi.prox

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class ProxyBackendHandlerTest {
    @Test
    fun `forwards plain minecraft frames without an outer codec`() {
        val frontend = EmbeddedChannel()
        val backend = EmbeddedChannel(ProxyBackendHandler(frontend))
        val expected = byteArrayOf(2, 2, 0)

        try {
            assertFalse(backend.writeInbound(Unpooled.wrappedBuffer(expected)))

            val forwarded = frontend.readOutbound<ByteBuf>()
            try {
                val actual = ByteArray(forwarded.readableBytes())
                forwarded.getBytes(forwarded.readerIndex(), actual)
                assertContentEquals(expected, actual)
            } finally {
                forwarded.release()
            }
        } finally {
            backend.finishAndReleaseAll()
            frontend.finishAndReleaseAll()
        }
    }
}
