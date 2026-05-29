package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration

object EndGatewayChorusIsland {
    @JvmStatic
    fun generateChorus(level: ServerLevel, islandCenter: BlockPos) {
        val random = RandomSource.create(islandCenter.asLong())
        val radius = 5
        for (pos in BlockPos.randomBetweenClosed(
            random,
            20,
            islandCenter.x - radius,
            islandCenter.y,
            islandCenter.z - radius,
            islandCenter.x + radius,
            islandCenter.y,
            islandCenter.z + radius
        )) {
            if (canGenerateChorus(level, pos)
                && Feature.CHORUS_PLANT.place(
                    FeatureConfiguration.NONE,
                    level,
                    level.getChunkSource().getGenerator(),
                    random,
                    pos.above()
                )
            ) {
                return
            }
        }
    }

    @JvmStatic
    fun findGatewayLocation(level: LevelReader, islandCenter: BlockPos): BlockPos {
        return BlockPos.withinManhattanStream(islandCenter, 7, 0, 7)
            .filter { pos: BlockPos ->
                level.getBlockState(pos).`is`(Blocks.END_STONE)
                        && Direction.stream().allMatch { direction ->
                    level.isEmptyBlock(pos.above(11).relative(direction))
                }
                        && Direction.stream().allMatch { direction ->
                    level.isEmptyBlock(pos.above(9).relative(direction))
                }
            }
            .findFirst()
            .orElse(islandCenter)
    }

    private fun canGenerateChorus(level: LevelReader, pos: BlockPos): Boolean {
        return level.getBlockState(pos).`is`(Blocks.END_STONE)
                && level.isEmptyBlock(pos.above())
                && Direction.Plane.HORIZONTAL.stream().noneMatch { direction ->
            level.isEmptyBlock(pos.relative(direction))
        }
    }
}
