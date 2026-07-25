package calebxzhou.rdi.client.proxy

import calebxzhou.rdi.client.service.ClientDirs
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Inflater

internal object LocalMcProxyMetricsConfig {
    val enabled: Boolean = System.getProperty("rdi.netMetrics").toBoolean()
}

class LocalMcProxyMetricsSession private constructor(
    private val connectionId: String,
    private val startedAtMs: Long
) {
    private val metrics = linkedMapOf<MetricKey, PacketMetric>()
    private var compressionEnabled = false
    private var closed = false

    @Synchronized
    fun record(direction: String, frame: ByteBuf) {
        if (closed) return
        val now = System.currentTimeMillis()
        val frameSize = frame.readableBytes()
        val parsed = parseFrame(frame)
        if (direction == "s2c" && !compressionEnabled && parsed.compressionThreshold != null) {
            compressionEnabled = true
        }
        val packetId = parsed.packetId?.let { "0x${it.toString(16)}" } ?: "unknown"
        val key = MetricKey(direction, packetId)
        val metric = metrics.getOrPut(key) { PacketMetric(firstSeenMs = now - startedAtMs) }
        metric.packetCount++
        metric.totalFrameSizeBytes += frameSize.toLong()
        metric.totalPacketLength += parsed.packetLength.toLong()
        metric.lastSeenMs = now - startedAtMs
    }

    @Synchronized
    fun closeAndSave(): File? {
        if (closed) return null
        closed = true
        if (metrics.isEmpty()) return null
        val dir = ClientDirs.mcDir.parentFile.resolve("net-metrics").apply { mkdirs() }
        val file = dir.resolve("localmcproxy-$connectionId.csv")
        file.writeText(buildCsv())
        return file
    }

    private fun buildCsv(): String = buildString {
        appendLine("connection_id,direction,packet_id,packet_count,total_frame_size_bytes,total_packet_length,first_seen_ms,last_seen_ms")
        metrics.forEach { (key, metric) ->
            append(connectionId).append(',')
            append(key.direction).append(',')
            append(key.packetId).append(',')
            append(metric.packetCount).append(',')
            append(metric.totalFrameSizeBytes).append(',')
            append(metric.totalPacketLength).append(',')
            append(metric.firstSeenMs).append(',')
            append(metric.lastSeenMs).appendLine()
        }
    }

    private fun parseFrame(frame: ByteBuf): ParsedPacket {
        val input = frame.slice()
        val packetLength = input.readVarIntOrNull() ?: return ParsedPacket(frame.readableBytes(), null)
        if (input.readableBytes() < packetLength) {
            return ParsedPacket(packetLength, null)
        }
        val payload = input.readSlice(packetLength)
        if (!compressionEnabled) {
            val packetId = payload.readVarIntOrNull()
            val compressionThreshold = if (packetId == 0x03) payload.readVarIntOrNull() else null
            return ParsedPacket(packetLength, packetId, compressionThreshold)
        }

        val dataLength = payload.readVarIntOrNull() ?: return ParsedPacket(packetLength, null)
        if (dataLength == 0) {
            return ParsedPacket(packetLength, payload.readVarIntOrNull())
        }

        val packetPayload = inflate(payload, dataLength)
        return try {
            ParsedPacket(packetLength, packetPayload.readVarIntOrNull())
        } finally {
            packetPayload.release()
        }
    }

    private fun inflate(input: ByteBuf, expectedSize: Int): ByteBuf {
        val compressed = ByteArray(input.readableBytes())
        input.getBytes(input.readerIndex(), compressed)
        val output = ByteArray(expectedSize)
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val inflatedSize = inflater.inflate(output)
            if (inflatedSize == expectedSize) {
                Unpooled.wrappedBuffer(output)
            } else {
                Unpooled.wrappedBuffer(output, 0, inflatedSize)
            }
        } finally {
            inflater.end()
        }
    }

    companion object {
        private val counter = AtomicLong()
        private val timestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT)

        fun create(): LocalMcProxyMetricsSession {
            val now = System.currentTimeMillis()
            val id = "${timestampFormat.format(Date(now))}-${counter.incrementAndGet()}"
            return LocalMcProxyMetricsSession(id, now)
        }
    }
}

private data class MetricKey(
    val direction: String,
    val packetId: String
)

private data class PacketMetric(
    var packetCount: Long = 0,
    var totalFrameSizeBytes: Long = 0,
    var totalPacketLength: Long = 0,
    var firstSeenMs: Long,
    var lastSeenMs: Long = firstSeenMs
)

private data class ParsedPacket(
    val packetLength: Int,
    val packetId: Int?,
    val compressionThreshold: Int? = null
)

private fun ByteBuf.readVarIntOrNull(): Int? {
    var value = 0
    var position = 0
    while (true) {
        if (!isReadable) return null
        val currentByte = readByte().toInt()
        value = value or ((currentByte and 0x7F) shl position)
        if ((currentByte and 0x80) == 0) return value
        position += 7
        if (position >= 35) return null
    }
}
