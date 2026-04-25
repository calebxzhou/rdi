package calebxzhou.rdi.master.model

import kotlinx.serialization.Serializable


@Serializable
data class WsMessage<T>(
    val id: Int,
    val channel: Channel,
    val data: T,
) {

    @Serializable
    enum class Channel {
        Command,
        Response,
        Chat,
    }
}
