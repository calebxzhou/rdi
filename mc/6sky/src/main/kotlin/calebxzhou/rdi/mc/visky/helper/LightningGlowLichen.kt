package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightningRodBlock
import net.minecraft.world.level.block.MultifaceBlock
import net.minecraft.world.level.block.VineBlock
import net.minecraft.world.level.block.state.BlockState

object LightningGlowLichen {
    @JvmStatic
    fun tryConvert(level: Level, strikePos: BlockPos) {
        if (level.isClientSide()) {
            return
        }

        val hitPos = getConductedHitPos(level, strikePos)
        val hitState = level.getBlockState(hitPos)
        if (!hitState.`is`(Blocks.GLOWSTONE)) {
            return
        }

        Direction.values().forEach { direction ->
            convertVine(level, hitPos.relative(direction), direction.opposite)
        }
    }

    private fun getConductedHitPos(level: Level, strikePos: BlockPos): BlockPos {
        val state = level.getBlockState(strikePos)
        if (state.block !is LightningRodBlock) {
            return strikePos
        }
        return strikePos.relative(state.getValue(LightningRodBlock.FACING).opposite)
    }

    private fun convertVine(level: Level, vinePos: BlockPos, attachedFace: Direction) {
        val vineState = level.getBlockState(vinePos)
        if (!isVineAttachedToGlowstone(vineState, attachedFace)) {
            return
        }

        val glowLichenState = Blocks.GLOW_LICHEN.defaultBlockState()
            .setValue(MultifaceBlock.getFaceProperty(attachedFace), true)
        level.setBlockAndUpdate(vinePos, glowLichenState)
    }

    private fun isVineAttachedToGlowstone(state: BlockState, attachedFace: Direction): Boolean {
        return state.`is`(Blocks.VINE) && state.getValue(VineBlock.getPropertyForFace(attachedFace))
    }
}
