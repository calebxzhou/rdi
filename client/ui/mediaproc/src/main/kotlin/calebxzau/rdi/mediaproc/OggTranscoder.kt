package calebxzau.rdi.mediaproc

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder_by_name
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_ERROR
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

class MediaProcUnavailableException(cause: Throwable) :
    IllegalStateException("Media processing native library is unavailable", cause)

object OggTranscoder {
    private const val OUTPUT_CODEC_NAME = "vorbis"
    private const val OUTPUT_SAMPLE_RATE = 16_000
    private const val OUTPUT_BITRATE = 96_000
    private const val MAX_DURATION_MICROS = 5_000_000L
    private const val MAX_CONCURRENCY = 8

    private val semaphore = Semaphore(MAX_CONCURRENCY)
    private val readiness: Result<Unit> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            loadNativeLibraries()
            val encoder: AVCodec? = avcodec_find_encoder_by_name(OUTPUT_CODEC_NAME)
            check(encoder != null) { "$OUTPUT_CODEC_NAME encoder is unavailable" }
        }
    }

    fun ensureReady(): Result<Unit> = readiness

    suspend fun transcodeOgg(input: ByteArray, tempDir: Path): Result<ByteArray> =
        semaphore.withPermit {
            ensureReady().fold(
                onSuccess = { transcodeWithTemporaryFiles(input, tempDir) },
                onFailure = { Result.failure(MediaProcUnavailableException(it)) }
            )
    }

    private fun loadNativeLibraries() {
        FFmpegFrameGrabber.tryLoad()
        FFmpegFrameRecorder.tryLoad()
        av_log_set_level(AV_LOG_ERROR)
    }

    private fun transcodeWithTemporaryFiles(input: ByteArray, tempDir: Path): Result<ByteArray> {
        var inputFile: File? = null
        var outputFile: File? = null
        return try {
            Files.createDirectories(tempDir)
            inputFile = Files.createTempFile(tempDir, "ogg-", ".input.ogg").toFile()
            outputFile = Files.createTempFile(tempDir, "ogg-", ".output.ogg").toFile()
            inputFile.writeBytes(input)
            transcodeFile(inputFile, outputFile)
            Result.success(outputFile.readBytes())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            runCatching { inputFile?.delete() }
            runCatching { outputFile?.delete() }
        }
    }

    private fun transcodeFile(inputFile: File, outputFile: File) {
        var grabber: FFmpegFrameGrabber? = null
        var recorder: FFmpegFrameRecorder? = null
        try {
            grabber = FFmpegFrameGrabber(inputFile)
            grabber.start()
            val inputChannels = grabber.audioChannels
            check(inputChannels > 0) { "OGG has no audio channels" }
            val outputChannels = if (inputChannels == 1) 2 else inputChannels

            recorder = FFmpegFrameRecorder(outputFile, outputChannels)
            recorder.format = "ogg"
            recorder.audioCodecName = OUTPUT_CODEC_NAME
            recorder.audioBitrate = OUTPUT_BITRATE
            recorder.sampleRate = OUTPUT_SAMPLE_RATE
            recorder.start()

            var recorded = false
            while (true) {
                val frame = grabber.grabSamples() ?: break
                if (grabber.timestamp >= MAX_DURATION_MICROS) break
                recorder.record(frame)
                recorded = true
            }
            check(recorded) { "OGG contains no decodable audio frames" }
            recorder.stop()
            recorder.release()
            recorder = null
            grabber.stop()
            grabber.release()
            grabber = null
            check(outputFile.exists() && outputFile.length() > 0L) {
                "FFmpeg produced an empty OGG file"
            }
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { grabber?.stop() }
            runCatching { grabber?.release() }
        }
    }
}
