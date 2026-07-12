package calebxzhou.rdi.proxy2.stun

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer

object StunCodec {
    private const val BINDING_REQUEST = 0x0001
    private const val BINDING_SUCCESS_RESPONSE = 0x0101
    private const val XOR_MAPPED_ADDRESS = 0x0020
    private const val MAGIC_COOKIE = 0x2112A442
    private const val HEADER_SIZE = 20

    fun isBindingRequest(packet: ByteArray, length: Int): Boolean {
        if (length < HEADER_SIZE) return false
        val buf = ByteBuffer.wrap(packet, 0, length)
        val type = buf.short.toInt() and 0xffff
        val messageLength = buf.short.toInt() and 0xffff
        val cookie = buf.int
        return type == BINDING_REQUEST && cookie == MAGIC_COOKIE && HEADER_SIZE + messageLength <= length
    }

    fun bindingResponse(request: ByteArray, requestLength: Int, remote: InetSocketAddress): ByteArray {
        require(isBindingRequest(request, requestLength)) { "not a STUN Binding Request" }

        val transactionId = request.copyOfRange(8, 20)
        val attrValue = xorMappedAddress(transactionId, remote)
        val attrPadding = padding(attrValue.size)
        val messageLength = 4 + attrValue.size + attrPadding
        val response = ByteBuffer.allocate(HEADER_SIZE + messageLength)

        response.putShort(BINDING_SUCCESS_RESPONSE.toShort())
        response.putShort(messageLength.toShort())
        response.putInt(MAGIC_COOKIE)
        response.put(transactionId)
        response.putShort(XOR_MAPPED_ADDRESS.toShort())
        response.putShort(attrValue.size.toShort())
        response.put(attrValue)
        repeat(attrPadding) { response.put(0.toByte()) }
        return response.array()
    }

    private fun xorMappedAddress(transactionId: ByteArray, remote: InetSocketAddress): ByteArray {
        val address = remote.address
        val port = remote.port xor (MAGIC_COOKIE ushr 16)
        return when (address) {
            is Inet4Address -> {
                val result = ByteBuffer.allocate(8)
                result.put(0.toByte())
                result.put(0x01.toByte())
                result.putShort(port.toShort())
                val cookieBytes = ByteBuffer.allocate(4).putInt(MAGIC_COOKIE).array()
                address.address.forEachIndexed { index, byte ->
                    result.put((byte.toInt() xor cookieBytes[index].toInt()).toByte())
                }
                result.array()
            }
            is Inet6Address -> {
                val result = ByteBuffer.allocate(20)
                result.put(0.toByte())
                result.put(0x02.toByte())
                result.putShort(port.toShort())
                val xorBytes = ByteBuffer.allocate(16)
                    .putInt(MAGIC_COOKIE)
                    .put(transactionId)
                    .array()
                address.address.forEachIndexed { index, byte ->
                    result.put((byte.toInt() xor xorBytes[index].toInt()).toByte())
                }
                result.array()
            }
            else -> error("unsupported address family: ${address.javaClass.name}")
        }
    }

    private fun padding(length: Int): Int =
        (4 - length % 4) % 4
}
