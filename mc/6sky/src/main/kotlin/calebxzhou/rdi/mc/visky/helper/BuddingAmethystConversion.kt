package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.FluidState

object BuddingAmethystConversion {
    private const val CONVERSION_RATE = 100

    @JvmStatic
    fun tryConvert(level: Level, pos: BlockPos, fluidState: FluidState, random: RandomSource): Boolean {
        if (level.isClientSide() || !fluidState.isSource || random.nextInt(CONVERSION_RATE) != 0) {
            return false
        }
        if (!isFormation(level, pos)) {
            return false
        }

        level.setBlockAndUpdate(pos, Blocks.BUDDING_AMETHYST.defaultBlockState())
        level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f + (random.nextFloat() - random.nextFloat()) * 0.8f)
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0f, 0.5f + random.nextFloat() * 1.2f)
        return true
    }

    private fun isFormation(level: Level, center: BlockPos): Boolean {
        if (!level.getBlockState(center).`is`(Blocks.LAVA)) {
            return false
        }

        val calciteOffsets = arrayOf(
            Offset(0, 1, 0),
            Offset(0, -1, 0),
            Offset(0, 0, 1),
            Offset(0, 0, -1),
            Offset(1, 0, 0),
            Offset(-1, 0, 0)
        )
        if (calciteOffsets.any { !level.getBlockState(center.offset(it.x, it.y, it.z)).`is`(Blocks.CALCITE) }) {
            return false
        }

        val smoothBasaltOffsets = arrayOf(
            Offset(0, 2, 0),
            Offset(0, -2, 0),
            Offset(0, 0, 2),
            Offset(0, 0, -2),
            Offset(2, 0, 0),
            Offset(-2, 0, 0),
            Offset(0, 1, 1),
            Offset(0, 1, -1),
            Offset(0, -1, 1),
            Offset(0, -1, -1),
            Offset(1, 0, 1),
            Offset(1, 0, -1),
            Offset(-1, 0, 1),
            Offset(-1, 0, -1),
            Offset(1, 1, 0),
            Offset(1, -1, 0),
            Offset(-1, 1, 0),
            Offset(-1, -1, 0)
        )
        return smoothBasaltOffsets.all { level.getBlockState(center.offset(it.x, it.y, it.z)).`is`(Blocks.SMOOTH_BASALT) }
    }

    private data class Offset(val x: Int, val y: Int, val z: Int)
}
