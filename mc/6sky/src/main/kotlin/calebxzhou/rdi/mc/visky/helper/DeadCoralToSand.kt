package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.Vec3

object DeadCoralToSand {
    private const val BREAK_CHANCE = 0.03f

    @JvmStatic
    fun getDelay(random: RandomSource): Int {
        return 320 + random.nextInt(320)
    }

    @JvmStatic
    fun tryDropSand(state: BlockState, level: Level, pos: BlockPos, random: RandomSource): Boolean {
        if (!isDeadCoral(state)) {
            return false
        }

        val fluidState = level.getFluidState(pos)
        if (!fluidState.`is`(Fluids.WATER)) {
            return false
        }

        val waterVelocity = fluidState.getFlow(level, pos)
        if (waterVelocity == Vec3.ZERO) {
            return false
        }

        if (!level.isClientSide()) {
            val sandVelocity = waterVelocity.scale(0.1)
            val itemEntity = ItemEntity(
                level,
                pos.getX() + 0.5,
                pos.getY().toDouble(),
                pos.getZ() + 0.5,
                ItemStack(getSandItem(state)),
                sandVelocity.x(),
                sandVelocity.y(),
                sandVelocity.z()
            )
            itemEntity.setDefaultPickUpDelay()
            level.addFreshEntity(itemEntity)
        }

        if (random.nextFloat() < BREAK_CHANCE) {
            level.removeBlock(pos, false)
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.5f, 1.0f)
            return false
        }

        return true
    }

    @JvmStatic
    fun isDeadCoral(state: BlockState): Boolean {
        return state.`is`(Blocks.DEAD_TUBE_CORAL)
                || state.`is`(Blocks.DEAD_BRAIN_CORAL)
                || state.`is`(Blocks.DEAD_BUBBLE_CORAL)
                || state.`is`(Blocks.DEAD_FIRE_CORAL)
                || state.`is`(Blocks.DEAD_HORN_CORAL)
                || state.`is`(Blocks.DEAD_TUBE_CORAL_FAN)
                || state.`is`(Blocks.DEAD_BRAIN_CORAL_FAN)
                || state.`is`(Blocks.DEAD_BUBBLE_CORAL_FAN)
                || state.`is`(Blocks.DEAD_FIRE_CORAL_FAN)
                || state.`is`(Blocks.DEAD_HORN_CORAL_FAN)
                || state.`is`(Blocks.DEAD_TUBE_CORAL_WALL_FAN)
                || state.`is`(Blocks.DEAD_BRAIN_CORAL_WALL_FAN)
                || state.`is`(Blocks.DEAD_BUBBLE_CORAL_WALL_FAN)
                || state.`is`(Blocks.DEAD_FIRE_CORAL_WALL_FAN)
                || state.`is`(Blocks.DEAD_HORN_CORAL_WALL_FAN)
    }

    private fun getSandItem(state: BlockState): Item {
        return if (isDeadFireCoral(state)) Items.RED_SAND else Items.SAND
    }

    private fun isDeadFireCoral(state: BlockState): Boolean {
        return state.`is`(Blocks.DEAD_FIRE_CORAL)
                || state.`is`(Blocks.DEAD_FIRE_CORAL_FAN)
                || state.`is`(Blocks.DEAD_FIRE_CORAL_WALL_FAN)
    }
}
