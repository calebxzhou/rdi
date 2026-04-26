package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import paulscode.sound.Library;
import paulscode.sound.Source;

import java.util.HashMap;

@Mixin(targets = "net.minecraft.client.audio.SoundManager$SoundSystemStarterThread")
public class mSoundManager {
    @Redirect(
            method = "playing(Ljava/lang/String;)Z",
            at = @At(value = "INVOKE", target = "Lpaulscode/sound/Library;getSources()Ljava/util/HashMap;")
    )
    private HashMap<String, Source> RDI$emptySourcesWhenNull(Library library) {
        HashMap<String, Source> sources = library.getSources();
        return sources == null ? new HashMap<>() : sources;
    }
}
