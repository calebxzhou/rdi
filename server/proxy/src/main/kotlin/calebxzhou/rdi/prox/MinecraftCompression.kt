package calebxzhou.rdi.prox

import calebxzau.util.netty.readVarInt
import calebxzau.util.netty.varIntSize
import calebxzau.util.netty.writeVarInt
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import java.util.zip.Deflater
import java.util.zip.Inflater

internal object MinecraftCompression {
    private const val MAX_FRAME_PAYLOAD_SIZE = 0x1FFFFF
    private const val MAX_UNCOMPRESSED_SIZE = 8 * 1024 * 1024
    private const val DEFLATE_BUFFER_SIZE = 8192

    fun peekPacketId(frame: ByteBuf, compressionThreshold: Int?): Result<Int> = runCatching {
        val input = frame.duplicate()
        val frameLength = input.readVarInt(maxBytes = 3)
        require(frameLength == input.readableBytes()) {
            "Frame length mismatch: declared=$frameLength actual=${input.readableBytes()}"
        }
        if (compressionThreshold == null) {
            return@runCatching input.readVarInt()
        }

        val dataLength = input.readVarInt()
        if (dataLength == 0) {
            return@runCatching input.readVarInt()
        }
        require(dataLength in compressionThreshold..MAX_UNCOMPRESSED_SIZE) {
            "Invalid decompressed packet size: $dataLength threshold=$compressionThreshold"
        }

        val inflater = Inflater()
        val output = ByteArray(1)
        try {
            inflater.setInput(input.nioBuffer(input.readerIndex(), input.readableBytes()))
            var value = 0
            var index = 0
            while (index < 5) {
                require(inflater.inflate(output) == 1) { "Unable to decompress Minecraft packet id" }
                val currentByte = output[0].toInt() and 0xFF
                value = value or ((currentByte and 0x7F) shl (index * 7))
                if ((currentByte and 0x80) == 0) return@runCatching value
                index++
            }
            error("Packet id VarInt is too big")
        } finally {
            inflater.end()
        }
    }

    fun decodePacketData(
        frame: ByteBuf,
        compressionThreshold: Int?,
        allocator: ByteBufAllocator
    ): Result<ByteBuf> = runCatching {
        val input = frame.duplicate()
        val frameLength = input.readVarInt(maxBytes = 3)
        require(frameLength == input.readableBytes()) {
            "Frame length mismatch: declared=$frameLength actual=${input.readableBytes()}"
        }
        if (compressionThreshold == null) {
            return@runCatching allocator.buffer(input.readableBytes()).apply {
                writeBytes(input)
            }
        }

        val dataLength = input.readVarInt()
        if (dataLength == 0) {
            allocator.buffer(input.readableBytes()).apply {
                writeBytes(input)
            }
        } else {
            require(dataLength in compressionThreshold..MAX_UNCOMPRESSED_SIZE) {
                "Invalid decompressed packet size: $dataLength threshold=$compressionThreshold"
            }
            val decompressed = ByteArray(dataLength)
            val inflater = Inflater()
            try {
                inflater.setInput(input.nioBuffer(input.readerIndex(), input.readableBytes()))
                var actualLength = 0
                while (!inflater.finished() && actualLength < dataLength) {
                    val read = inflater.inflate(decompressed, actualLength, dataLength - actualLength)
                    require(read > 0) { "Unable to continue decompressing Minecraft packet" }
                    actualLength += read
                }
                require(actualLength == dataLength && inflater.finished()) {
                    "Decompressed packet size mismatch: actual=$actualLength declared=$dataLength"
                }
            } finally {
                inflater.end()
            }
            allocator.buffer(dataLength).apply {
                writeBytes(decompressed)
            }
        }
    }

    fun encodePacketData(
        packetData: ByteBuf,
        compressionThreshold: Int?,
        allocator: ByteBufAllocator
    ): Result<ByteBuf> = runCatching {
        val packetSize = packetData.readableBytes()
        val body = allocator.buffer()
        try {
            if (compressionThreshold == null || packetSize < compressionThreshold) {
                if (compressionThreshold != null) {
                    body.writeVarInt(0)
                }
                body.writeBytes(packetData, packetData.readerIndex(), packetSize)
            } else {
                require(packetSize <= MAX_UNCOMPRESSED_SIZE) {
                    "Uncompressed packet too large: $packetSize"
                }
                body.writeVarInt(packetSize)
                val deflater = Deflater()
                val buffer = ByteArray(DEFLATE_BUFFER_SIZE)
                try {
                    deflater.setInput(packetData.nioBuffer(packetData.readerIndex(), packetSize))
                    deflater.finish()
                    while (!deflater.finished()) {
                        val size = deflater.deflate(buffer)
                        body.writeBytes(buffer, 0, size)
                    }
                } finally {
                    deflater.end()
                }
            }

            val framePayloadSize = body.readableBytes()
            require(framePayloadSize <= MAX_FRAME_PAYLOAD_SIZE) {
                "Minecraft frame payload too large: $framePayloadSize"
            }
            allocator.buffer(varIntSize(framePayloadSize) + framePayloadSize).apply {
                writeVarInt(framePayloadSize)
                writeBytes(body, body.readerIndex(), framePayloadSize)
            }
        } finally {
            body.release()
        }
    }
}
