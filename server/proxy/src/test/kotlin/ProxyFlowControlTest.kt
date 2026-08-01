package calebxzhou.rdi.prox

import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.embedded.EmbeddedChannel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyFlowControlTest {
    @Test
    fun `pauses source when target reaches its write watermark`() {
        val source = EmbeddedChannel()
        val target = EmbeddedChannel()
        ProxyFlowControl.bind(source, target)

        try {
            target.config().setOption(ChannelOption.WRITE_BUFFER_WATER_MARK, WriteBufferWaterMark(1, 2))
            target.unsafe().outboundBuffer()?.setUserDefinedWritability(1, false)
            assertFalse(target.isWritable)

            ProxyFlowControl.pauseSourceIfTargetNotWritable(source, target)

            assertFalse(source.config().isAutoRead)
        } finally {
            source.finishAndReleaseAll()
            target.finishAndReleaseAll()
        }
    }

    @Test
    fun `resumes peer after target becomes writable`() {
        val source = EmbeddedChannel()
        val target = EmbeddedChannel()
        ProxyFlowControl.bind(source, target)
        source.config().isAutoRead = false

        try {
            ProxyFlowControl.resumePeerIfWritable(target)
            assertTrue(source.config().isAutoRead)
        } finally {
            source.finishAndReleaseAll()
            target.finishAndReleaseAll()
        }
    }
}
