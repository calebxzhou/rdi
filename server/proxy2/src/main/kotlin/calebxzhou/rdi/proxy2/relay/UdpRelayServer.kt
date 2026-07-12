package calebxzhou.rdi.proxy2.relay

import calebxzhou.rdi.proxy2.Proxy2Config
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val udpLgr = KotlinLogging.logger {}

class UdpRelayServer(
    private val config: Proxy2Config,
    private val metrics: Proxy2Metrics,
    private val sessions: RelaySessionManager,
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val nextSocket = DatagramSocket(InetSocketAddress(config.bindHost, config.udpRelayPort))
        socket = nextSocket
        thread(name = "proxy2-udp-relay", isDaemon = false) {
            runLoop(nextSocket)
        }
        udpLgr.info { "UDP relay listening on ${config.bindHost}:${config.udpRelayPort}" }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
    }

    private fun runLoop(socket: DatagramSocket) {
        val buffer = ByteArray(65_535)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                metrics.inc("udpRelayPacketsIn")
                val relayPacket = RelayPacketCodec.decodeUdp(packet.data, packet.length)
                if (relayPacket == null) {
                    metrics.inc("udpRelayInvalidPackets")
                    continue
                }
                val remote = InetSocketAddress(packet.address, packet.port)
                handlePacket(socket, relayPacket, remote)
            } catch (_: SocketException) {
                if (running.get()) metrics.inc("udpRelaySocketErrors")
            } catch (t: Throwable) {
                metrics.inc("udpRelayErrors")
                udpLgr.warn(t) { "UDP relay packet handling failed" }
            }
        }
    }

    private fun handlePacket(socket: DatagramSocket, packet: RelayPacket, remote: InetSocketAddress) {
        when (packet.type) {
            RelayPacketType.Register -> {
                sessions.registerUdpPeer(packet.sessionId, packet.peerId, remote)
                metrics.inc("udpRelayRegisters")
            }
            RelayPacketType.Data -> {
                val session = sessions.registerUdpPeer(packet.sessionId, packet.peerId, remote)
                val target = session.otherUdpPeer(packet.peerId)
                if (target == null) {
                    metrics.inc("udpRelayDroppedNoPeer")
                    return
                }
                val outbound = packet.encode()
                socket.send(DatagramPacket(outbound, outbound.size, target.address, target.port))
                session.touch()
                metrics.inc("udpRelayPacketsOut")
                metrics.inc("udpRelayBytesOut", outbound.size.toLong())
            }
            RelayPacketType.Ping -> {
                val response = packet.copy(type = RelayPacketType.Pong).encode()
                socket.send(DatagramPacket(response, response.size, remote.address, remote.port))
                metrics.inc("udpRelayPongs")
            }
            RelayPacketType.Pong -> {
                sessions.registerUdpPeer(packet.sessionId, packet.peerId, remote)
            }
            RelayPacketType.Close -> {
                sessions.closeUdpPeer(packet.sessionId, packet.peerId)
                metrics.inc("udpRelayCloses")
            }
        }
    }
}
