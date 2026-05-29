package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.function.Consumer
import kotlin.math.sqrt

object DeepslateConversion {
    val CONVERSION_POTION: Holder<Potion> = Potions.THICK

    @JvmStatic
    fun isConversionPotion(potionContents: PotionContents): Boolean {
        return potionContents.`is`(CONVERSION_POTION)
    }

    @JvmStatic
    fun convertWithBottle(level: Level, blockPos: BlockPos, eventPos: BlockPos): Boolean {
        val convertedState = getConvertedState(level.getBlockState(blockPos))
        if (convertedState.isEmpty) {
            return false
        }

        if (level is ServerLevel) {
            for (i in 0..4) {
                level.sendParticles<SimpleParticleType>(
                    ParticleTypes.SPLASH,
                    eventPos.getX() + level.getRandom().nextDouble(),
                    (eventPos.getY() + 1).toDouble(),
                    eventPos.getZ() + level.getRandom().nextDouble(),
                    1,
                    0.0,
                    0.0,
                    0.0,
                    1.0
                )
            }
        }

        level.playSound(null, eventPos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
        level.gameEvent(null, GameEvent.FLUID_PLACE, eventPos)
        level.setBlockAndUpdate(blockPos, convertedState.get())
        return true
    }

    @JvmStatic
    fun convertAtSplash(level: Level, hitPos: Vec3) {
        if (level.isClientSide()) {
            return
        }

        BlockPos.betweenClosedStream(AABB.ofSize(hitPos, 8.25, 4.25, 8.25)).forEach { pos: BlockPos ->
            val convertedState = getConvertedState(level.getBlockState(pos))
            if (convertedState.isPresent) {
                val distance = sqrt(pos.center.distanceToSqr(hitPos))
                if (level.getRandom().nextDouble() < getSplashConversionChance(distance)) {
                    level.setBlockAndUpdate(pos, convertedState.get())
                }
            }
        }
    }

    @JvmStatic
    fun convertInCloud(level: Level, box: AABB) {
        if (level.isClientSide()) {
            return
        }

        BlockPos.betweenClosedStream(box).forEach { pos: BlockPos ->
            val convertedState = getConvertedState(level.getBlockState(pos))
            convertedState.ifPresent(Consumer { blockState: BlockState -> level.setBlockAndUpdate(pos, blockState) })
        }
    }

    private fun getConvertedState(state: BlockState): Optional<BlockState> {
        if (state.`is`(Blocks.STONE)) {
            return Optional.of(Blocks.DEEPSLATE.defaultBlockState())
        }
        return Optional.empty<BlockState>()
    }

    private fun getSplashConversionChance(distance: Double): Double {
        val multiplier = Mth.clamp(1.0 - distance / 4.0, 0.0, 1.0)
        return Mth.clamp(2.0 * multiplier, 0.0, 1.0)
    }
}
