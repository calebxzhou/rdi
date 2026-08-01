package calebxzau.rdi.mediaproc

import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber.SampleMode
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

data class PcmAudioFormat(
    val sampleRate: Int,
    val channels: Int
)

class FfmpegPcmStream internal constructor(
    private val grabber: FFmpegFrameGrabber,
    val format: PcmAudioFormat
) : Closeable {
    private var pending = ByteBuffer.allocate(0)
    private var endOfStream = false
    private var closed = false

    fun read(maxBytes: Int): Result<ByteBuffer> = runCatching {
        check(!closed) { "PCM stream is closed" }
        require(maxBytes > 0) { "maxBytes must be positive" }
        val frameSize = format.channels * Short.SIZE_BYTES
        val outputSize = maxBytes - maxBytes % frameSize
        require(outputSize > 0) { "maxBytes must contain at least one PCM frame" }
        val output = ByteBuffer.allocateDirect(outputSize).order(ByteOrder.LITTLE_ENDIAN)

        while (output.hasRemaining()) {
            if (pending.hasRemaining()) {
                copyBytes(pending, output)
                continue
            }
            if (endOfStream) break
            pending = grabNextFrame()
        }
        output.flip()
        output
    }

    fun readAll(): Result<ByteBuffer> = runCatching {
        val output = ByteArrayOutputStream()
        while (true) {
            val chunk = read(64 * 1024).getOrThrow()
            if (!chunk.hasRemaining()) break
            val bytes = ByteArray(chunk.remaining())
            chunk.get(bytes)
            output.write(bytes)
        }
        ByteBuffer.allocateDirect(output.size())
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(output.toByteArray())
            .flip()
    }

    fun closeResult(): Result<Unit> = runCatching {
        if (closed) return@runCatching
        closed = true
        grabber.release()
    }

    override fun close() {
        closeResult().getOrThrow()
    }

    private fun grabNextFrame(): ByteBuffer {
        val frame = grabber.grabSamples()
        if (frame == null) {
            endOfStream = true
            return ByteBuffer.allocate(0)
        }
        val samples = frame.samples
        check(samples.size == 1 && samples[0] is ShortBuffer) {
            "FFmpeg did not produce interleaved 16-bit PCM"
        }
        val shorts = (samples[0] as ShortBuffer).duplicate()
        return ByteBuffer.allocateDirect(shorts.remaining() * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                while (shorts.hasRemaining()) putShort(shorts.get())
                flip()
            }
    }

    private fun copyBytes(source: ByteBuffer, target: ByteBuffer) {
        val originalLimit = source.limit()
        source.limit(minOf(source.position() + target.remaining(), originalLimit))
        target.put(source)
        source.limit(originalLimit)
    }
}

object FfmpegPcmDecoder {
    private val readiness: Result<Unit> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            FFmpegFrameGrabber.tryLoad()
            val decoder: AVCodec? = avcodec_find_decoder(AV_CODEC_ID_OPUS)
            check(decoder != null) { "Opus decoder is unavailable" }
        }
    }

    fun ensureOpusReady(): Result<Unit> = readiness

    fun open(input: InputStream): Result<FfmpegPcmStream> = runCatching {
        ensureOpusReady().getOrThrow()
        val grabber = FFmpegFrameGrabber(input, 0).apply {
            sampleMode = SampleMode.SHORT
            start()
        }
        try {
            check(grabber.hasAudio()) { "OGG has no audio stream" }
            val outputChannels = if (grabber.audioChannels == 1) 1 else 2
            grabber.audioChannels = outputChannels
            FfmpegPcmStream(
                grabber,
                PcmAudioFormat(
                    sampleRate = grabber.sampleRate,
                    channels = outputChannels
                )
            )
        } catch (error: Throwable) {
            runCatching { grabber.release() }
            throw error
        }
    }
}
