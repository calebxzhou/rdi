package calebxzhou.rdi.mc.visky.mixin;

import calebxzhou.rdi.mc.visky.SkyblockWorldGen;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * calebxzhou @ 2026-05-25 22:58
 */
@Mixin(LevelStem.class)
public class mEmptyLevel {
    @Shadow
    @Final
    @Mutable
    private ChunkGenerator generator;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void RDI$OnInit(Holder<DimensionType> type, ChunkGenerator generator, CallbackInfo ci) {
        //空岛模式 all 空白生成
        if (generator instanceof NoiseBasedChunkGenerator ng) {
            this.generator = new SkyblockWorldGen(ng.getBiomeSource(), ng.generatorSettings());
        }
    }
}
