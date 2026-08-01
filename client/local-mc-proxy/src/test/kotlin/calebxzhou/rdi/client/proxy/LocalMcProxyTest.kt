package calebxzhou.rdi.client.proxy

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMcProxyTest {
    @Test
    fun `start returns actual loopback address and is idempotent`() {
        val proxy = LocalMcProxy(
            endpointProvider = { ProxyEndpoint("127.0.0.1", 25565) },
            config = LocalMcProxyConfig(
                preferredBindPort = 0
            )
        )

        try {
            val firstAddress = proxy.start().getOrThrow()
            assertTrue(firstAddress.startsWith("127.0.0.1:"))
            assertTrue(firstAddress.substringAfterLast(':').toInt() > 0)
            assertEquals(firstAddress, proxy.start().getOrThrow())
        } finally {
            proxy.stop().getOrThrow()
        }
    }

    @Test
    fun `relays plain minecraft frames and permits immediate reconnect`() {
        val backend = ServerSocket(0)
        val frames = encodeFrames(
            byteArrayOf(0x00),
            byteArrayOf(0x01, 0x02, 0x03)
        )
        val errors = ConcurrentLinkedQueue<Throwable>()
        val served = CountDownLatch(2)
        val backendThread = thread(start = true, isDaemon = true, name = "local-mc-proxy-test-backend") {
            try {
                repeat(2) {
                    backend.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val received = socket.getInputStream().readNBytes(frames.size)
                        assertContentEquals(frames, received)
                        socket.getOutputStream().apply {
                            write(received)
                            flush()
                        }
                        served.countDown()
                    }
                }
            } catch (error: Throwable) {
                errors += error
            }
        }
        val proxy = LocalMcProxy(
            endpointProvider = { ProxyEndpoint("127.0.0.1", backend.localPort) },
            config = LocalMcProxyConfig(preferredBindPort = 0)
        )

        try {
            val localAddress = proxy.start().getOrThrow()
            repeat(2) {
                Socket("127.0.0.1", localAddress.substringAfterLast(':').toInt()).use { socket ->
                    socket.soTimeout = 5_000
                    socket.getOutputStream().apply {
                        write(frames)
                        flush()
                    }
                    assertContentEquals(frames, socket.getInputStream().readNBytes(frames.size))
                }
            }
            assertTrue(served.await(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), errors.joinToString())
        } finally {
            proxy.stop().getOrThrow()
            backend.close()
            backendThread.join(1_000)
        }
    }

    private fun encodeFrames(vararg payloads: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        payloads.forEach { payload ->
            writeVarInt(payload.size, output)
            output.write(payload)
        }
        return output.toByteArray()
    }

    private fun writeVarInt(value: Int, output: ByteArrayOutputStream) {
        var remaining = value
        do {
            var next = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) next = next or 0x80
            output.write(next)
        } while (remaining != 0)
    }
}
