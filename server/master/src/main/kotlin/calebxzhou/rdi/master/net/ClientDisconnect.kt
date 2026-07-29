package calebxzhou.rdi.master.net

import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.ClosedWriteChannelException
import java.nio.channels.ClosedChannelException

internal fun Throwable.isClientDisconnect(): Boolean = generateSequence(this) { it.cause }.any {
    it is ClosedByteChannelException ||
            it is ClosedWriteChannelException ||
            it is ChannelWriteException ||
            it is ClosedChannelException
}
