package calebxzau.rdi.mediaproc

import org.bytedeco.javacv.FFmpegFrameRecorder
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.nio.ShortBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FfmpegPcmDecoderTest {
    @Test
    fun detectsVorbisOgg() {
        val codec = javaClass.getResourceAsStream("/assets/empty.ogg")!!.buffered().use {
            OggCodecDetector.detect(it).getOrThrow()
        }

        assertEquals(OggAudioCodec.VORBIS, codec)
    }

    @Test
    fun detectsAndDecodesOpusOgg() {
        assumeWindowsX64()
        val opusOgg = createOpusOgg()

        val codec = BufferedInputStream(opusOgg.inputStream()).use {
            OggCodecDetector.detect(it).getOrThrow()
        }
        assertEquals(OggAudioCodec.OPUS, codec)

        val stream = FfmpegPcmDecoder.open(opusOgg.inputStream()).getOrThrow()
        try {
            assertEquals(48_000, stream.format.sampleRate)
            assertEquals(1, stream.format.channels)
            assertTrue(stream.readAll().getOrThrow().hasRemaining())
        } finally {
            stream.closeResult().getOrThrow()
        }
    }

    @Test
    fun chunkedReadMatchesReadAll() {
        assumeWindowsX64()
        val opusOgg = createOpusOgg()
        val allAtOnce = FfmpegPcmDecoder.open(opusOgg.inputStream()).getOrThrow().use {
            it.readAll().getOrThrow().toByteArray()
        }
        val chunked = FfmpegPcmDecoder.open(opusOgg.inputStream()).getOrThrow().use { stream ->
            val output = ByteArrayOutputStream()
            while (true) {
                val bytes = stream.read(4096).getOrThrow().toByteArray()
                if (bytes.isEmpty()) break
                output.write(bytes)
            }
            output.toByteArray()
        }

        assertContentEquals(allAtOnce, chunked)
    }

    @Test
    fun invalidInputReturnsFailure() {
        assumeWindowsX64()
        assertTrue(FfmpegPcmDecoder.open(byteArrayOf(1, 2, 3).inputStream()).isFailure)
    }

    private fun createOpusOgg(): ByteArray {
        val outputFile = Files.createTempFile("mediaproc-opus-", ".ogg").toFile()
        val recorder = FFmpegFrameRecorder(outputFile, 1).apply {
            format = "ogg"
            audioCodecName = "libopus"
            sampleRate = 48_000
            audioChannels = 1
        }
        try {
            recorder.start()
            recorder.recordSamples(48_000, 1, ShortBuffer.wrap(ShortArray(4_800)))
            recorder.stop()
            return outputFile.readBytes()
        } finally {
            runCatching { recorder.release() }
            outputFile.delete()
        }
    }

    private fun java.nio.ByteBuffer.toByteArray(): ByteArray =
        ByteArray(remaining()).also(::get)

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
