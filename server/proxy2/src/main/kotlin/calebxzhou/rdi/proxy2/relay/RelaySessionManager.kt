package calebxzhou.rdi.proxy2.relay

import calebxzhou.rdi.proxy2.Proxy2Config
import calebxzhou.rdi.proxy2.metrics.Proxy2Metrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RelaySessionManager(
    private val config: Proxy2Config,
    private val metrics: Proxy2Metrics,
    scope: CoroutineScope,
) {
    private val sessions = ConcurrentHashMap<UUID, RelaySession>()

    init {
        scope.launch {
            while (isActive) {
                delay(config.cleanupIntervalMs)
                cleanup()
            }
        }
    }

    fun activeSessionCount(): Int = sessions.size

    fun registerUdpPeer(sessionId: UUID, peerId: UUID, endpoint: InetSocketAddress): RelaySession {
        val now = System.currentTimeMillis()
        val session = sessions.computeIfAbsent(sessionId) { RelaySession(sessionId, now) }
        session.registerUdpPeer(peerId, endpoint)
        metrics.set("activeRelaySessions", sessions.size.toLong())
        return session
    }

    fun touchSession(sessionId: UUID): RelaySession {
        val now = System.currentTimeMillis()
        val session = sessions.computeIfAbsent(sessionId) { RelaySession(sessionId, now) }
        session.touch(now)
        metrics.set("activeRelaySessions", sessions.size.toLong())
        return session
    }

    fun find(sessionId: UUID): RelaySession? = sessions[sessionId]

    fun closeUdpPeer(sessionId: UUID, peerId: UUID) {
        sessions[sessionId]?.removeUdpPeer(peerId)
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        var expired = 0L
        sessions.entries.removeIf { (_, session) ->
            val remove = now - session.lastActiveAtMs() > config.sessionTtlMs
            if (remove) expired++
            remove
        }
        if (expired > 0) metrics.inc("expiredSessions", expired)
        metrics.set("activeRelaySessions", sessions.size.toLong())
    }
}
