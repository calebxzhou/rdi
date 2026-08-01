package calebxzau.rdi.mediaproc

import kotlinx.coroutines.runBlocking
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.ShortBuffer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OggTranscoderTest {
    @Test
    fun ensureReadyLoadsWindowsX64NativeVorbisCodec() {
        assumeWindowsX64()
        assertTrue(OggTranscoder.ensureReady().isSuccess)
    }

    @Test
    fun transcodeProducesVorbisAtTargetSampleRate() {
        assumeWindowsX64()
        val input = javaClass.getResourceAsStream("/assets/empty.ogg")!!.use { it.readBytes() }
        val tempDir = Files.createTempDirectory("mediaproc-test-")
        try {
            val output = runBlocking {
                OggTranscoder.transcodeOgg(input, tempDir).getOrThrow()
            }
            assertTrue(output.isNotEmpty())

            val outputFile = Files.createTempFile(tempDir, "result-", ".ogg").toFile()
            outputFile.writeBytes(output)
            val grabber = FFmpegFrameGrabber(outputFile)
            try {
                grabber.start()
                assertEquals("ogg", grabber.format)
                assertEquals("vorbis", grabber.audioCodecName)
                assertEquals(16_000, grabber.sampleRate)
                assertTrue(grabber.hasAudio())
                grabber.stop()
            } finally {
                runCatching { grabber.release() }
                outputFile.delete()
            }
            assertFalse(Files.list(tempDir).use { it.findAny().isPresent })
        } finally {
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun transcodesMonoOggToStereoWithNativeVorbis() {
        assumeWindowsX64()
        val tempDir = Files.createTempDirectory("mediaproc-test-")
        try {
            val inputFile = Files.createTempFile(tempDir, "mono-", ".ogg").toFile()
            val inputRecorder = FFmpegFrameRecorder(inputFile, 1).apply {
                format = "ogg"
                audioCodecName = "libopus"
                sampleRate = 48_000
            }
            try {
                inputRecorder.start()
                inputRecorder.recordSamples(48_000, 1, ShortBuffer.wrap(ShortArray(4_800)))
                inputRecorder.stop()
            } finally {
                inputRecorder.release()
            }

            val output = runBlocking {
                OggTranscoder.transcodeOgg(inputFile.readBytes(), tempDir).getOrThrow()
            }
            val outputFile = Files.createTempFile(tempDir, "result-", ".ogg").toFile()
            outputFile.writeBytes(output)
            val grabber = FFmpegFrameGrabber(outputFile)
            try {
                grabber.start()
                assertEquals("vorbis", grabber.audioCodecName)
                assertEquals(2, grabber.audioChannels)
                grabber.stop()
            } finally {
                grabber.release()
            }
        } finally {
            Files.list(tempDir).use { files -> files.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    fun invalidInputReturnsFailureAndCleansTemporaryFiles() {
        assumeWindowsX64()
        val tempDir = Files.createTempDirectory("mediaproc-test-")
        try {
            val result = runBlocking {
                OggTranscoder.transcodeOgg(byteArrayOf(1, 2, 3), tempDir)
            }
            assertTrue(result.isFailure)
            assertFalse(Files.list(tempDir).use { it.findAny().isPresent })
        } finally {
            Files.deleteIfExists(tempDir)
        }
    }

    private fun assumeWindowsX64() {
        val osName = System.getProperty("os.name").orEmpty()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        assumeTrue(osName.contains("windows", ignoreCase = true))
        assumeTrue(osArch == "amd64" || osArch == "x86_64")
    }
}
