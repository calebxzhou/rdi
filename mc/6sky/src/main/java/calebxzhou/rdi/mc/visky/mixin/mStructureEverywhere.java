package calebxzhou.rdi.mc.visky.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * calebxzhou @ 2026-05-25 22:57
 */
@Mixin(StructureCheck.class)
public class mStructureEverywhere {
    @Overwrite
    private boolean canCreateStructure(ChunkPos pChunkPos, Structure pStructure) {
        return true;
    }
}

@Mixin(Structure.class)
class mStructureEverywhere2 {
    @Overwrite
    private static boolean isValidBiome(Structure.GenerationStub pStub, Structure.GenerationContext pContext) {
        return true;
    }
}