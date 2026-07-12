package calebxzhou.rdi.proxy2.relay

import java.nio.ByteBuffer
import java.util.UUID

private const val RELAY_VERSION: Byte = 1
private const val RELAY_HEADER_SIZE = 34
private const val TCP_HELLO_SIZE = 33

enum class RelayPacketType(val id: Byte) {
    Register(0x01),
    Data(0x02),
    Ping(0x03),
    Pong(0x04),
    Close(0x05);

    companion object {
        fun byId(id: Byte): RelayPacketType? = entries.firstOrNull { it.id == id }
    }
}

data class RelayPacket(
    val type: RelayPacketType,
    val sessionId: UUID,
    val peerId: UUID,
    val payload: ByteArray,
) {
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(RELAY_HEADER_SIZE + payload.size)
        buffer.put(RELAY_VERSION)
        buffer.put(type.id)
        buffer.putUuid(sessionId)
        buffer.putUuid(peerId)
        buffer.put(payload)
        return buffer.array()
    }
}

data class TcpRelayHello(
    val sessionId: UUID,
    val peerId: UUID,
)

object RelayPacketCodec {
    fun decodeUdp(bytes: ByteArray, length: Int): RelayPacket? {
        if (length < RELAY_HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes, 0, length)
        val version = buffer.get()
        if (version != RELAY_VERSION) return null
        val type = RelayPacketType.byId(buffer.get()) ?: return null
        val sessionId = buffer.getUuid()
        val peerId = buffer.getUuid()
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)
        return RelayPacket(type, sessionId, peerId, payload)
    }

    fun decodeTcpHello(bytes: ByteArray): TcpRelayHello? {
        if (bytes.size != TCP_HELLO_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes)
        val version = buffer.get()
        if (version != RELAY_VERSION) return null
        return TcpRelayHello(
            sessionId = buffer.getUuid(),
            peerId = buffer.getUuid(),
        )
    }
}

private fun ByteBuffer.putUuid(uuid: UUID) {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
}

private fun ByteBuffer.getUuid(): UUID =
    UUID(getLong(), getLong())
