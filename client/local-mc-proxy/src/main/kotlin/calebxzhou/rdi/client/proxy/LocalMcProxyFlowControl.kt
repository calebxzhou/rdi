package calebxzhou.rdi.client.proxy

import io.netty.channel.Channel
import io.netty.util.AttributeKey

internal object LocalMcProxyFlowControl {
    private val PEER_CHANNEL = AttributeKey.valueOf<Channel>("rdi.localmcproxy.peer")

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
