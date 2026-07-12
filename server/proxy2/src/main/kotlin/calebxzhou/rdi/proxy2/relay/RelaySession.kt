package calebxzhou.rdi.proxy2.relay

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class RelaySession(
    val id: UUID,
    nowMs: Long,
) {
    val createdAtMs: Long = nowMs
    private val lastActiveAtMs = AtomicLong(nowMs)
    private val udpPeers = ConcurrentHashMap<UUID, InetSocketAddress>()

    fun touch(nowMs: Long = System.currentTimeMillis()) {
        lastActiveAtMs.set(nowMs)
    }

    fun lastActiveAtMs(): Long = lastActiveAtMs.get()

    fun registerUdpPeer(peerId: UUID, endpoint: InetSocketAddress) {
        udpPeers[peerId] = endpoint
        touch()
    }

    fun removeUdpPeer(peerId: UUID) {
        udpPeers.remove(peerId)
        touch()
    }

    fun otherUdpPeer(peerId: UUID): InetSocketAddress? =
        udpPeers.entries.firstOrNull { it.key != peerId }?.value

    fun udpPeerCount(): Int = udpPeers.size
}
