package calebxzhou.rdi.mc.server.mixin;

import calebxzhou.rdi.mc.server.firmsection.FirmSectionService112;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.util.Constants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilChunkLoader.class)
public class mOnlySaveFirmSectionEntities112 {
    @Inject(method = "writeChunkToNBT", at = @At("TAIL"))
    private void rdi$onlySaveFirmSectionEntities(Chunk chunk, World world, NBTTagCompound levelTag, CallbackInfo ci) {
        if (!FirmSectionService112.INSTANCE.isEnabled()) {
            return;
        }
        NBTTagList entities = levelTag.getTagList("Entities", Constants.NBT.TAG_COMPOUND);
        if (entities.tagCount() == 0) {
            return;
        }
        NBTTagList filtered = new NBTTagList();
        for (int i = 0; i < entities.tagCount(); i++) {
            NBTTagCompound entityTag = entities.getCompoundTagAt(i);
            NBTTagList pos = entityTag.getTagList("Pos", Constants.NBT.TAG_DOUBLE);
            if (pos.tagCount() >= 3 && FirmSectionService112.INSTANCE.shouldSaveEntityPosition(
                    world,
                    pos.getDoubleAt(0),
                    pos.getDoubleAt(1),
                    pos.getDoubleAt(2))) {
                filtered.appendTag(entityTag);
            }
        }
        levelTag.setTag("Entities", filtered);
        chunk.setHasEntities(filtered.tagCount() > 0);
    }
}
