package calebxzhou.rdi.mc.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.InputStream;

@Mixin(Window.class)
public class mWindow {
    @Unique
    private static final Logger RDI$LOGGER = LoggerFactory.getLogger("rdi-window-icon");

    @Redirect(
            method = "setIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"
            )
    )
    private NativeImage rdi$readWindowIcon(InputStream inputStream) {
        try {
            return NativeImage.read(inputStream);
        } catch (IOException | RuntimeException exception) {
            RDI$LOGGER.warn("忽略损坏的窗口图标", exception);
            return new NativeImage(1, 1, false);
        }
    }

    @Redirect(
            method = "setIcon",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/IoSupplier;get()Ljava/lang/Object;"
            )
    )
    private Object rdi$getWindowIcon(IoSupplier<?> supplier) throws IOException {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            RDI$LOGGER.warn("忽略无法读取的窗口图标资源", exception);
            throw new IOException("Failed to read window icon", exception);
        }
    }
}
