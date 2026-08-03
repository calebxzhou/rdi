package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.sound.RSoundStreamFactory;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.FiniteAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public abstract class mSoundBufferLibrary {
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
            try (
                    InputStream input = resourceManager.open(location);
                    FiniteAudioStream stream = RSoundStreamFactory.open(input)
            ) {
                return new SoundBuffer(stream.readAll(), stream.getFormat());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, Util.nonCriticalIoPool())));
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void rdi$decodeStream(
            ResourceLocation location,
            boolean looping,
            CallbackInfoReturnable<CompletableFuture<AudioStream>> cir
    ) {
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                InputStream input = resourceManager.open(location);
                return looping
                        ? new LoopingAudioStream(RSoundStreamFactory::open, input)
                        : RSoundStreamFactory.open(input);
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }, Util.nonCriticalIoPool()));
    }
}
