package calebxzhou.rdi.mc.visky

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.WorldGenerationContext
import net.minecraft.world.level.levelgen.blending.Blender
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class SkyblockWorldGen(
    biomeSource: BiomeSource,
    settings: Holder<NoiseGeneratorSettings>
) : NoiseBasedChunkGenerator(biomeSource, settings) {
    //空岛模式 取消地形生成
    override fun buildSurface(
        chunk: ChunkAccess,
        context: WorldGenerationContext,
        random: RandomState,
        structureManager: StructureManager,
        biomeManager: BiomeManager,
        biomes: Registry<Biome?>,
        blender: Blender
    ) {

    }

    override fun applyCarvers(
        level: WorldGenRegion,
        seed: Long,
        random: RandomState,
        biomeManager: BiomeManager,
        structureManager: StructureManager,
        chunk: ChunkAccess,
        step: GenerationStep.Carving
    ) {

    }

    override fun fillFromNoise(
        blender: Blender,
        random: RandomState,
        structureManager: StructureManager,
        chunk: ChunkAccess
    ): CompletableFuture<ChunkAccess> {
        return CompletableFuture.completedFuture(chunk)
    }

}