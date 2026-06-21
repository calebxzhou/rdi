package calebxzhou.rdi.mc.server.mixin;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AnvilChunkLoader.class)
public interface AAnvilChunkLoader1710 {
    @Invoker("writeChunkToNBT")
    void rdi$writeChunkToNbt(Chunk chunk, World world, NBTTagCompound levelTag);
}
