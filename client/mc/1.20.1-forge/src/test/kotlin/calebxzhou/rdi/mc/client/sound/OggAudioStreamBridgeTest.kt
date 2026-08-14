package calebxzhou.rdi.mc.client.sound

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class OggAudioStreamBridgeTest {
    @Test
    fun bufferInputPreservesTheOriginalPrefix() {
        val bytes = "OggS-prefix".encodeToByteArray()
        val buffered = OggAudioStreamBridge.bufferInput(ByteArrayInputStream(bytes))

        assertIs<BufferedInputStream>(buffered)
        assertContentEquals(bytes, buffered.readNBytes(bytes.size))
    }

    @Test
    fun sharedStartupFixtureStillUsesOpus() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/assets/rdi/sounds/mc_start.ogg")) {
            "shared mc_start.ogg fixture is missing"
        }.use { it.readBytes() }

        assertContentEquals("OggS".encodeToByteArray(), bytes.copyOfRange(0, 4))
        assertContentEquals("OpusHead".encodeToByteArray(), bytes.copyOfRange(28, 36))
    }
}
