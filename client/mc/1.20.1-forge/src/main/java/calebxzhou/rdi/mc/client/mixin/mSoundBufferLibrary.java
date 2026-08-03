package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.sound.RSoundStreamFactory;
import calebxzau.rdi.mediaproc.FfmpegPcmDecoder;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public abstract class mSoundBufferLibrary {
    private static final int READ_CHUNK_BYTES = 64 * 1024;
    @Unique
    private static final Logger RDI$LOGGER = LoggerFactory.getLogger("rdi-opus-stream");

    @Shadow @Final
    private ResourceProvider resourceManager;

    @Shadow @Final
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void rdi$decodeCompleteBuffer(
            ResourceLocation soundId,
            CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir
    ) {
        cir.setReturnValue(cache.computeIfAbsent(soundId, location -> CompletableFuture.supplyAsync(() -> {
            try (AudioStream stream = RSoundStreamFactory.open(resourceManager.open(location))) {
                AudioFormat format = stream.getFormat();
                return new SoundBuffer(readComplete(stream), format);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, Util.backgroundExecutor())));
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void rdi$decodeStream(
            ResourceLocation location,
            boolean looping,
            CallbackInfoReturnable<CompletableFuture<AudioStream>> cir
    ) {
        CompletableFuture<AudioStream> future = CompletableFuture.supplyAsync(() -> {
            try {
                InputStream input = resourceManager.open(location);
                if (!looping) {
                    return RSoundStreamFactory.open(input);
                }
                try {
                    return new LoopingAudioStream(RSoundStreamFactory::open, input);
                } catch (IOException error) {
                    closeAfterLoopOpenFailure(input, error);
                    throw error;
                }
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, Util.backgroundExecutor());
        future.whenComplete((stream, error) -> {
            if (error != null) {
                RDI$LOGGER.error("音频stream异步加载失败: {}", location, error);
            }
        });
        cir.setReturnValue(future);
    }

    private static ByteBuffer readComplete(AudioStream stream) throws IOException {
        int frameSize = stream.getFormat().getFrameSize();
        if (frameSize <= 0) {
            throw new IOException("Audio stream has no frame size");
        }
        int maxBytes = FfmpegPcmDecoder.DEFAULT_MAX_COMPLETE_PCM_BYTES;
        int boundedSize = maxBytes - maxBytes % frameSize;
        List<ByteBuffer> chunks = new ArrayList<>();
        int totalBytes = 0;
        while (true) {
            int remaining = boundedSize - totalBytes;
            if (remaining == 0) {
                ByteBuffer extra = stream.read(frameSize);
                if (extra.hasRemaining()) {
                    throw new IOException("Decoded PCM exceeds " + maxBytes + " bytes");
                }
                break;
            }

            int request = Math.min(READ_CHUNK_BYTES, remaining);
            request -= request % frameSize;
            if (request <= 0) {
                throw new IOException("Complete audio limit cannot contain one audio frame");
            }
            ByteBuffer chunk = stream.read(request);
            if (!chunk.hasRemaining()) {
                break;
            }
            int chunkBytes = chunk.remaining();
            if (chunkBytes > remaining) {
                throw new IOException("Audio stream returned more data than requested");
            }
            totalBytes += chunkBytes;
            chunks.add(chunk);
        }

        ByteBuffer output = ByteBuffer.allocateDirect(totalBytes);
        for (ByteBuffer chunk : chunks) {
            output.put(chunk.duplicate());
        }
        output.flip();
        return output;
    }

    private static void closeAfterLoopOpenFailure(InputStream input, IOException error) {
        try {
            input.close();
        } catch (IOException closeError) {
            error.addSuppressed(closeError);
        }
    }
}
