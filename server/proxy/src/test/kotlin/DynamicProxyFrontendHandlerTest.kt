package calebxzhou.rdi.prox

import calebxzhou.rdi.common.model.HostStatus
import calebxzhou.rdi.common.model.ProxyHostRoute
import calebxzhou.rdi.common.model.Response
import calebxzhou.rdi.mc.proxy.MinecraftFrameDecoder
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class DynamicProxyFrontendHandlerTest {
    @Test
    fun `routes plain minecraft frames without zstd framing`() {
        val backend = ServerSocket(0)
        val backendGroup = NioEventLoopGroup(1)
        val handshake = handshakeFrame()
        val response = byteArrayOf(2, 2, 0)
        val received = CountDownLatch(1)
        val backendError = arrayListOf<Throwable>()
        val backendThread = thread(start = true, isDaemon = true, name = "proxy-test-backend") {
            try {
                backend.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val actual = socket.getInputStream().readNBytes(handshake.size)
                    assertContentEquals(handshake, actual)
                    socket.getOutputStream().apply {
                        write(response)
                        flush()
                    }
                    received.countDown()
                }
            } catch (error: Throwable) {
                backendError += error
            }
        }
        val routeResolver = ProxyRouteResolver(request = {
            Response(
                code = 0,
                msg = "",
                data = ProxyHostRoute(HostStatus.PLAYABLE, "127.0.0.1", backend.localPort)
            )
        })
        val frontend = EmbeddedChannel(
            MinecraftFrameDecoder(),
            DynamicProxyFrontendHandler(
                defaultBackendHost = "127.0.0.1",
                defaultBackendPort = backend.localPort,
                backendGroup = backendGroup,
                routeResolver = routeResolver
            )
        )

        try {
            frontend.writeInbound(Unpooled.wrappedBuffer(handshake))
            repeat(200) {
                frontend.runPendingTasks()
                frontend.runScheduledPendingTasks()
                if (frontend.outboundMessages().isNotEmpty()) return@repeat
                Thread.sleep(10)
            }
            assertTrue(received.await(5, TimeUnit.SECONDS))
            frontend.runPendingTasks()
            val forwarded = frontend.readOutbound<ByteBuf>()
            try {
                val actual = ByteArray(forwarded.readableBytes())
                forwarded.getBytes(forwarded.readerIndex(), actual)
                assertContentEquals(response, actual)
            } finally {
                forwarded.release()
            }
            assertTrue(backendError.isEmpty(), backendError.joinToString())
        } finally {
            frontend.finishAndReleaseAll()
            backend.close()
            backendGroup.shutdownGracefully().syncUninterruptibly()
            backendThread.join(1_000)
        }
    }

    private fun handshakeFrame(): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(0)
            writeVarInt(763)
            writeVarInt(1)
            write('x'.code)
            write(0x63)
            write(0xDD)
            write(2)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeVarInt(payload.size)
            write(payload)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value
        do {
            var next = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) next = next or 0x80
            write(next)
        } while (remaining != 0)
    }
}
