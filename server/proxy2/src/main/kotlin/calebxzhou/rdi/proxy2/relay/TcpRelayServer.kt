package calebxzhou.rdi.proxy2.relay

import calebxzhou.rdi.proxy2.Proxy2Config
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private val tcpLgr = KotlinLogging.logger {}

class TcpRelayServer(
    private val config: Proxy2Config,
    private val metrics: Proxy2Metrics,
    private val sessions: RelaySessionManager,
) {
    private val running = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<UUID, PendingTcpPeer>()
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val nextServerSocket = ServerSocket(config.tcpRelayPort, 128, java.net.InetAddress.getByName(config.bindHost))
        serverSocket = nextServerSocket
        thread(name = "proxy2-tcp-relay-accept", isDaemon = false) {
            acceptLoop(nextServerSocket)
        }
        thread(name = "proxy2-tcp-relay-cleanup", isDaemon = true) {
            cleanupLoop()
        }
        tcpLgr.info { "TCP relay listening on ${config.bindHost}:${config.tcpRelayPort}" }
    }

    fun stop() {
        running.set(false)
        serverSocket?.close()
        pending.values.forEach { it.socket.closeQuietly() }
        pending.clear()
        serverSocket = null
    }

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (running.get()) {
            try {
                val socket = serverSocket.accept()
                metrics.inc("tcpRelayConnections")
                thread(name = "proxy2-tcp-relay-client", isDaemon = true) {
                    handleClient(socket)
                }
            } catch (_: SocketException) {
                if (running.get()) metrics.inc("tcpRelaySocketErrors")
            } catch (t: Throwable) {
                metrics.inc("tcpRelayErrors")
                tcpLgr.warn(t) { "TCP relay accept failed" }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 10_000
            val helloBytes = socket.getInputStream().readFully(33)
            val hello = RelayPacketCodec.decodeTcpHello(helloBytes)
            if (hello == null || !RelayToken.acceptDebugToken(hello.sessionId.toString(), hello.peerId.toString())) {
                metrics.inc("tcpRelayInvalidHello")
                socket.closeQuietly()
                return
            }
            sessions.touchSession(hello.sessionId)
            pairOrWait(hello, socket)
        } catch (_: EOFException) {
            metrics.inc("tcpRelayShortHello")
            socket.closeQuietly()
        } catch (t: Throwable) {
            metrics.inc("tcpRelayClientErrors")
            tcpLgr.warn(t) { "TCP relay client handling failed" }
            socket.closeQuietly()
        }
    }

    private fun pairOrWait(hello: TcpRelayHello, socket: Socket) {
        while (running.get()) {
            val current = pending[hello.sessionId]
            if (current == null) {
                val mine = PendingTcpPeer(hello.peerId, socket, System.currentTimeMillis())
                if (pending.putIfAbsent(hello.sessionId, mine) == null) {
                    metrics.set("tcpRelayPendingSessions", pending.size.toLong())
                    return
                }
                continue
            }
            if (current.peerId == hello.peerId) {
                pending[hello.sessionId] = PendingTcpPeer(hello.peerId, socket, System.currentTimeMillis()).also {
                    current.socket.closeQuietly()
                }
                metrics.inc("tcpRelayReplacedDuplicatePeer")
                return
            }
            if (pending.remove(hello.sessionId, current)) {
                metrics.set("tcpRelayPendingSessions", pending.size.toLong())
                startPipe(hello.sessionId, current.socket, socket)
                return
            }
        }
        socket.closeQuietly()
    }

    private fun startPipe(sessionId: UUID, a: Socket, b: Socket) {
        a.soTimeout = 0
        b.soTimeout = 0
        metrics.inc("tcpRelayPairedSessions")
        tcpLgr.info { "paired TCP relay session $sessionId" }
        thread(name = "proxy2-tcp-relay-a2b", isDaemon = true) {
            pipe(a, b)
        }
        thread(name = "proxy2-tcp-relay-b2a", isDaemon = true) {
            pipe(b, a)
        }
    }

    private fun pipe(from: Socket, to: Socket) {
        val buffer = ByteArray(32 * 1024)
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            while (running.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
                metrics.inc("tcpRelayBytesOut", read.toLong())
            }
        } catch (_: SocketException) {
            metrics.inc("tcpRelayPipeClosed")
        } catch (t: Throwable) {
            metrics.inc("tcpRelayPipeErrors")
            tcpLgr.debug(t) { "TCP relay pipe failed" }
        } finally {
            from.closeQuietly()
            to.closeQuietly()
        }
    }

    private fun cleanupLoop() {
        while (running.get()) {
            Thread.sleep(config.cleanupIntervalMs)
            val now = System.currentTimeMillis()
            var expired = 0L
            pending.entries.removeIf { (_, peer) ->
                val remove = now - peer.createdAtMs > config.sessionTtlMs
                if (remove) {
                    peer.socket.closeQuietly()
                    expired++
                }
                remove
            }
            if (expired > 0) metrics.inc("tcpRelayExpiredPending", expired)
            metrics.set("tcpRelayPendingSessions", pending.size.toLong())
        }
    }

    private data class PendingTcpPeer(
        val peerId: UUID,
        val socket: Socket,
        val createdAtMs: Long,
    )
}

private fun java.io.InputStream.readFully(size: Int): ByteArray {
    val result = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(result, offset, size - offset)
        if (read < 0) throw EOFException()
        offset += read
    }
    return result
}

private fun Socket.closeQuietly() {
    runCatching { close() }
}
