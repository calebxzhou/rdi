package calebxzhou.rdi.prox

import io.netty.channel.Channel
import io.netty.util.AttributeKey

/**
 * Couples the read side of each relay channel to the writability of its peer.
 *
 * The relay deliberately uses Netty's channel watermarks as the queue bound.
 * No application-level byte queue is allowed to grow while the peer is blocked.
 */
internal object ProxyFlowControl {
    private val PEER_CHANNEL = AttributeKey.valueOf<Channel>("rdi.proxy.peer")

    fun bind(a: Channel, b: Channel) {
        a.attr(PEER_CHANNEL).set(b)
        b.attr(PEER_CHANNEL).set(a)
    }

    fun pauseSourceIfTargetNotWritable(source: Channel, target: Channel) {
        if (!target.isWritable) source.config().isAutoRead = false
    }

    fun resumePeerIfWritable(channel: Channel) {
        if (!channel.isWritable) return
        val peer = channel.attr(PEER_CHANNEL).get() ?: return
        if (peer.isActive) {
            peer.config().isAutoRead = true
            peer.read()
        }
    }
}
