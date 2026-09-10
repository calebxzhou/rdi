package calebxzhou.rdi.mc.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.core.WindowKJS", remap = false)
public interface mKubeJsWindowIcon {
    @Redirect(
            method = "kjs$loadIcons(Ljava/util/List;)Ljava/util/List;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;",
                    remap = false
            ),
            remap = false
    )
    private BufferedImage rdi$replaceNullIcon(InputStream input) throws IOException {
        BufferedImage image = ImageIO.read(input);
        return image == null ? new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) : image;
    }
}
