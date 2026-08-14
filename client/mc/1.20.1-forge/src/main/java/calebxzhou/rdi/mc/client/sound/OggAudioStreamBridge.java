package calebxzhou.rdi.mc.client.sound;

import net.minecraft.client.sounds.AudioStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Java-only entry points used by the Forge coremod transformer.
 *
 * <p>The transformer cannot call Kotlin's mangled Result-returning decoder methods directly. This
 * bridge keeps those details in the existing sound factory while giving the injected bytecode
 * stable Java descriptors.</p>
 */
public final class OggAudioStreamBridge {
    private OggAudioStreamBridge() {
    }

    /**
     * Gives the codec detector a mark/reset-capable stream without consuming the caller's input.
     */
    public static BufferedInputStream bufferInput(InputStream input) {
        return input instanceof BufferedInputStream
                ? (BufferedInputStream) input
                : new BufferedInputStream(input);
    }

    /**
     * Opens only Ogg/Opus. The detector restores the buffered stream position in all codec cases;
     * consequently a null return leaves the original Vorbis bytecode free to read from byte zero.
     */
    public static AudioStream openOpus(BufferedInputStream input) throws IOException {
        return RSoundStreamFactory.openOpusOrNull(input);
    }

    /**
     * Reads the FFmpeg delegate with its existing bounded PCM readAll implementation.
     */
    public static ByteBuffer readAll(AudioStream stream) throws IOException {
        if (!(stream instanceof FfmpegAudioStream)) {
            throw new IOException("Unexpected OGG delegate: " + stream.getClass().getName());
        }
        return ((FfmpegAudioStream) stream).readAll();
    }
}
