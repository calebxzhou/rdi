package calebxzau.rdi.client

import calebxzau.rdi.mediaproc.OggAudioCodec
import calebxzau.rdi.mediaproc.OggCodecDetector
import java.io.BufferedInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledAssetsTest {
    @Test
    fun sharedAssetsAreAvailableFromRuntimeClasspath() {
        listOf(
            "assets/bg.avif",
            "assets/empty.ogg",
            "assets/empty.mp3",
            "icon.png",
            "mcmeta/1.21.1.json",
            "launcher_profiles.json",
            "forge-install-bootstrapper.jar",
            "overrides/fancymenu-options.txt"
        ).forEach { path ->
            assertNotNull(javaClass.classLoader.getResource(path), "Missing bundled asset: $path")
        }
    }

    @Test
    fun sharedEmptySoundRemainsVanillaVorbis() {
        val codec = javaClass.classLoader.getResourceAsStream("assets/empty.ogg")!!.use { input ->
            BufferedInputStream(input).use { stream ->
                OggCodecDetector.detect(stream).getOrThrow()
            }
        }
        assertEquals(OggAudioCodec.VORBIS, codec)
    }

    @Test
    fun sharedEmptyMp3HasAnId3HeaderAndMpegFrame() {
        val bytes = javaClass.classLoader.getResourceAsStream("assets/empty.mp3")!!.use { it.readBytes() }
        assertTrue(bytes.size > 14)
        assertEquals("ID3", bytes.copyOfRange(0, 3).decodeToString())

        val tagSize = 10 + ((bytes[6].toInt() and 0x7f) shl 21) +
            ((bytes[7].toInt() and 0x7f) shl 14) +
            ((bytes[8].toInt() and 0x7f) shl 7) +
            (bytes[9].toInt() and 0x7f)
        assertTrue(tagSize + 2 <= bytes.size)
        assertEquals(0xff, bytes[tagSize].toInt() and 0xff)
        assertTrue(bytes[tagSize + 1].toInt() and 0xe0 == 0xe0)
    }
}
