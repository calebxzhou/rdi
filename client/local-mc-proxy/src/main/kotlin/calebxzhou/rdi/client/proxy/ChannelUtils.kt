package calebxzhou.rdi.client.proxy

import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener

internal fun Channel.closeOnFlush() {
    if (isActive) {
        writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
}
