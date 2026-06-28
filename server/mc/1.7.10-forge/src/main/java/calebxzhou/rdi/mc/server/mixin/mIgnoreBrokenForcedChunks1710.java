package calebxzhou.rdi.mc.server.mixin;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ReportedException;
import net.minecraftforge.common.ForgeChunkManager;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;

@Mixin(ForgeChunkManager.class)
public class mIgnoreBrokenForcedChunks1710 {
    @Redirect(
            method = "loadWorld",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/CompressedStreamTools;read(Ljava/io/File;)Lnet/minecraft/nbt/NBTTagCompound;",
                    remap = true
            )
    )
    private static NBTTagCompound rdi$readForcedChunkData(File file) throws IOException {
        try {
            return CompressedStreamTools.read(file);
        } catch (ReportedException e) {
            FMLLog.log(
                    Level.ERROR,
                    e,
                    "Unable to read forced chunk data at %s - all persisted forced chunk tickets for this world will be ignored",
                    file.getAbsolutePath()
            );
            return new NBTTagCompound();
        }
    }
}
