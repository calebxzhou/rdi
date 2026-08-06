package calebxzau.rdi.client

import calebxzau.rdi.mediaproc.OggAudioCodec
import calebxzau.rdi.mediaproc.OggCodecDetector
import java.io.BufferedInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BundledAssetsTest {
    @Test
    fun sharedAssetsAreAvailableFromRuntimeClasspath() {
        listOf(
            "assets/bg.avif",
            "assets/empty.ogg",
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
}
