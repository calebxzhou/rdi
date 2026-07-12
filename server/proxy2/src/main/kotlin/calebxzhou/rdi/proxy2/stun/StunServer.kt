package calebxzhou.rdi.proxy2.stun

import calebxzhou.rdi.proxy2.Proxy2Config
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val lgr = KotlinLogging.logger {}

class StunServer(
    private val config: Proxy2Config,
    private val metrics: Proxy2Metrics,
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var worker: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val nextSocket = DatagramSocket(InetSocketAddress(config.bindHost, config.stunPort))
        socket = nextSocket
        worker = thread(name = "proxy2-stun", isDaemon = false) {
            runLoop(nextSocket)
        }
        lgr.info { "STUN server listening on ${config.bindHost}:${config.stunPort}" }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
    }

    private fun runLoop(socket: DatagramSocket) {
        val buffer = ByteArray(1500)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                metrics.inc("stunRequests")
                val remote = InetSocketAddress(packet.address, packet.port)
                if (!StunCodec.isBindingRequest(packet.data, packet.length)) {
                    metrics.inc("stunIgnoredPackets")
                    continue
                }
                val response = StunCodec.bindingResponse(packet.data, packet.length, remote)
                socket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                metrics.inc("stunResponses")
            } catch (_: SocketException) {
                if (running.get()) metrics.inc("stunSocketErrors")
            } catch (t: Throwable) {
                metrics.inc("stunErrors")
                lgr.warn(t) { "STUN packet handling failed" }
            }
        }
    }
}
