package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider
import net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator

object HugeMushroomMycelium {
    @JvmStatic
    fun spread(level: WorldGenLevel, random: RandomSource, pos: BlockPos) {
        AlterGroundDecorator(BlockStateProvider.simple(Blocks.MYCELIUM)).place(
            TreeDecorator.Context(
                level,
                { blockPos, blockState -> level.setBlock(blockPos, blockState, Block.UPDATE_ALL) },
                random,
                setOf(pos),
                setOf(),
                setOf()
            )
        )
    }
}
