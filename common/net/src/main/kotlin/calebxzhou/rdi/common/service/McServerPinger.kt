package calebxzhou.rdi.common.service

import calebxzhou.rdi.common.serdesJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset

@Serializable
data class McServerStatusPayload(
    val version: McServerVersion? = null,
    val players: McServerPlayers? = null,
    val description: JsonElement? = null,
    val favicon: String? = null,
    @SerialName("enforcesSecureChat") val enforcesSecureChat: Boolean? = null,
    @SerialName("previewsChat") val previewsChat: Boolean? = null,
    val forgeData: JsonElement? = null,
    val modinfo: JsonElement? = null
)

@Serializable
data class McServerVersion(
    val name: String? = null,
    val protocol: Int? = null
)

@Serializable
data class McServerPlayers(
    val max: Int? = null,
    val online: Int? = null,
    val sample: List<McServerPlayerSample> = emptyList()
)

@Serializable
data class McServerPlayerSample(
    val name: String? = null,
    val id: String? = null
)

data class McServerPingResult(
    val latencyMillis: Long,
    val version: McServerVersion?,
    val players: McServerPlayers?,
    val descriptionText: String,
    val faviconBase64: String?,
    val enforcesSecureChat: Boolean?,
    val previewsChat: Boolean?,
    val forgeData: JsonElement?,
    val modInfo: JsonElement?,
    val ip: String,
    val port: Int,
    val raw: JsonObject
)

class McServerPingException(message: String) : RuntimeException(message)

object McServerPinger {
    private const val DEFAULT_PROTOCOL_VERSION = 763
    private const val STATUS_STATE = 1
    private const val HANDSHAKE_PACKET_ID = 0x00
    private const val STATUS_REQUEST_PACKET_ID = 0x00
    private const val STATUS_RESPONSE_PACKET_ID = 0x00
    private const val PING_PACKET_ID = 0x01

    suspend fun ping(
        port: Int,
        ip: String = "127.0.0.1",
        timeoutMillis: Int = 1_000,
        protocolVersion: Int = DEFAULT_PROTOCOL_VERSION,
        charset: Charset = Charsets.UTF_8
    ): McServerPingResult {
        try {
            return withContext(Dispatchers.IO) {
                withTimeout(timeoutMillis.toLong()) {
                    Socket().use { socket ->
                        socket.soTimeout = timeoutMillis
                        socket.connect(InetSocketAddress(ip, port), timeoutMillis)
                        executePing(socket, ip, port, protocolVersion, charset)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw McServerPingException("Minecraft 服务器状态请求超时 (${timeoutMillis}ms)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: McServerPingException) {
            throw e
        } catch (e: Throwable) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "未知错误"
            throw McServerPingException("Minecraft 服务器状态请求失败: $message")
        }
    }

    private fun executePing(
        socket: Socket,
        ip: String,
        port: Int,
        protocolVersion: Int,
        charset: Charset
    ): McServerPingResult {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        sendHandshake(output, ip, port, protocolVersion, charset)
        sendStatusRequest(output)

        val statusJson = readStatusResponse(input, charset)
        val latency = sendPingAndMeasureLatency(output, input)

        val jsonElement = serdesJson.parseToJsonElement(statusJson)
        val jsonObject = jsonElement as? JsonObject
            ?: throw McServerPingException("Minecraft 服务器返回的状态格式异常")
        val payload = serdesJson.decodeFromJsonElement<McServerStatusPayload>(jsonObject)
        return payload.toResult(latency, ip, port, jsonObject)
    }

    private fun sendHandshake(
        output: DataOutputStream,
        ip: String,
        port: Int,
        protocolVersion: Int,
        charset: Charset
    ) {
        val hostBytes = ip.toByteArray(charset)
        val handshakeBytes = ByteArrayOutputStream()
        DataOutputStream(handshakeBytes).use { handshake ->
            handshake.writeVarInt(HANDSHAKE_PACKET_ID)
            handshake.writeVarInt(protocolVersion)
            handshake.writeVarInt(hostBytes.size)
            handshake.write(hostBytes)
            handshake.writeShort(port)
            handshake.writeVarInt(STATUS_STATE)
        }
        val payload = handshakeBytes.toByteArray()
        output.writeVarInt(payload.size)
        output.write(payload)
        output.flush()
    }

    private fun sendStatusRequest(output: DataOutputStream) {
        output.writeByte(0x01)
        output.writeByte(STATUS_REQUEST_PACKET_ID)
        output.flush()
    }

    private fun readStatusResponse(input: DataInputStream, charset: Charset): String {
        val packetLength = input.readVarInt()
        if (packetLength <= 0) throw McServerPingException("Minecraft 服务器未返回状态数据")

        val packetId = input.readVarInt()
        if (packetId != STATUS_RESPONSE_PACKET_ID) {
            throw McServerPingException("Minecraft 服务器返回了未知的状态包 (id=$packetId)")
        }

        val jsonLength = input.readVarInt()
        val data = ByteArray(jsonLength)
        input.readFully(data)
        return String(data, charset)
    }

    private fun sendPingAndMeasureLatency(
        output: DataOutputStream,
        input: DataInputStream
    ): Long {
        val sentTimestamp = System.currentTimeMillis()
        val pingBytes = ByteArrayOutputStream()
        DataOutputStream(pingBytes).use {
            it.writeVarInt(PING_PACKET_ID)
            it.writeLong(sentTimestamp)
        }
        val payload = pingBytes.toByteArray()
        output.writeVarInt(payload.size)
        output.write(payload)
        output.flush()

        input.readVarInt()
        val echoedTimestamp = input.readLong()
        return (System.currentTimeMillis() - echoedTimestamp).coerceAtLeast(0)
    }

    private fun McServerStatusPayload.toResult(
        latencyMillis: Long,
        ip: String,
        port: Int,
        raw: JsonObject
    ) = McServerPingResult(
        latencyMillis = latencyMillis,
        version = version,
        players = players,
        descriptionText = description.collectTextOrEmpty(),
        faviconBase64 = favicon,
        enforcesSecureChat = enforcesSecureChat,
        previewsChat = previewsChat,
        forgeData = forgeData,
        modInfo = modinfo,
        ip = ip,
        port = port,
        raw = raw
    )
}

private fun DataInputStream.readVarInt(): Int {
    var numRead = 0
    var result = 0
    while (true) {
        val read = readByte().toInt() and 0xFF
        val value = read and 0x7F
        result = result or (value shl (7 * numRead))
        numRead++

        if (numRead > 5) {
            throw McServerPingException("收到了无效的 VarInt 编码")
        }

        if ((read and 0x80) != 0x80) {
            break
        }
    }
    return result
}

private fun DataOutputStream.writeVarInt(value: Int) {
    var v = value
    while (true) {
        if ((v and 0xFFFFFF80.toInt()) == 0) {
            writeByte(v)
            return
        }
        writeByte((v and 0x7F) or 0x80)
        v = v ushr 7
    }
}

private fun JsonElement?.collectTextOrEmpty(): String = when (this) {
    null -> ""
    else -> this.collectText()
}

private fun JsonElement.collectText(): String = when (this) {
    is JsonPrimitive -> this.contentOrNull ?: ""
    is JsonObject -> buildString {
        val text = this@collectText["text"].collectTextOrEmpty()
        if (text.isNotEmpty()) append(text)
        this@collectText["extra"]?.let { append(it.collectText()) }
        val translate = this@collectText["translate"]?.jsonPrimitive?.contentOrNull
        if (!translate.isNullOrEmpty() && text.isEmpty()) append(translate)
    }
    is JsonArray -> buildString {
        this@collectText.forEach { append(it.collectText()) }
    }
}
