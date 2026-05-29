package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.animal.Dolphin
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.phys.Vec3
import java.util.EnumSet

class DolphinFindHeartGoal(private val dolphin: Dolphin) : Goal() {
    private var digCounter = 0
    private var digging = false

    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun isInterruptable(): Boolean {
        return false
    }

    override fun canUse(): Boolean {
        return dolphin.gotFish() && dolphin.getAirSupply() >= 100
    }

    override fun canContinueToUse(): Boolean {
        return dolphin.gotFish() && dolphin.getAirSupply() >= 100
    }

    override fun start() {
        val level = dolphin.level()
        if (level !is ServerLevel) {
            return
        }

        val target = findOceanFloor(level)
        if (target == null) {
            fail(level)
            return
        }

        digging = false
        digCounter = 0
        dolphin.setTreasurePos(target)
        dolphin.getNavigation().moveTo(target.x + 0.5, target.y + 1.0, target.z + 0.5, 0.8)
        level.broadcastEntityEvent(dolphin, EntityEvent.DOLPHIN_LOOKING_FOR_TREASURE)
    }

    override fun stop() {
        digging = false
        digCounter = 0
        dolphin.getNavigation().stop()
    }

    override fun tick() {
        val level = dolphin.level()
        if (level !is ServerLevel) {
            return
        }

        val target = dolphin.getTreasurePos()
        if (!isValidOceanFloor(level, target)) {
            fail(level)
            return
        }

        val targetCenter = Vec3.atCenterOf(target.above())
        dolphin.getLookControl().setLookAt(
            targetCenter.x,
            targetCenter.y,
            targetCenter.z,
            dolphin.getMaxHeadYRot().toFloat() + 20.0f,
            dolphin.getMaxHeadXRot().toFloat()
        )

        if (!digging) {
            if (dolphin.position().closerThan(targetCenter, 3.0)) {
                digging = true
                digCounter = 0
                dolphin.getNavigation().stop()
            } else if (dolphin.getNavigation().isDone()) {
                fail(level)
            } else {
                dolphin.getNavigation().moveTo(target.x + 0.5, target.y + 1.0, target.z + 0.5, 0.8)
            }
            return
        }

        if (digCounter < NUM_DIGS) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, target, Block.getId(level.getBlockState(target)))
            digCounter++
            return
        }

        if (level.getRandom().nextFloat() < HEART_CHANCE) {
            val heartStack = ItemStack(Items.HEART_OF_THE_SEA)
            if (dolphin.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
                dolphin.setItemSlot(EquipmentSlot.MAINHAND, heartStack)
            } else {
                Block.popResource(level, target.above(), heartStack)
            }
            level.broadcastEntityEvent(dolphin, EntityEvent.DOLPHIN_LOOKING_FOR_TREASURE)
        } else {
            failParticles(level)
        }
        dolphin.setGotFish(false)
        digging = false
    }

    private fun findOceanFloor(level: ServerLevel): BlockPos? {
        val random = level.getRandom()
        val origin = dolphin.blockPosition()
        repeat(48) {
            val x = origin.x + random.nextInt(SEARCH_RANGE * 2 + 1) - SEARCH_RANGE
            val z = origin.z + random.nextInt(SEARCH_RANGE * 2 + 1) - SEARCH_RANGE
            val topY = minOf(origin.y + 4, level.getMaxBuildHeight() - 2)
            for (y in topY downTo level.getMinBuildHeight()) {
                val pos = BlockPos(x, y, z)
                if (isValidOceanFloor(level, pos)) {
                    return pos
                }
            }
        }
        return null
    }

    private fun isValidOceanFloor(level: ServerLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return (state.`is`(Blocks.SAND) || state.`is`(Blocks.GRAVEL))
                && level.getFluidState(pos.above()).`is`(FluidTags.WATER)
                && level.getBiome(pos.above()).`is`(BiomeTags.IS_OCEAN)
    }

    private fun fail(level: ServerLevel) {
        failParticles(level)
        dolphin.setGotFish(false)
        digging = false
        dolphin.getNavigation().stop()
    }

    private fun failParticles(level: ServerLevel) {
        level.sendParticles(
            ParticleTypes.WITCH,
            dolphin.getRandomX(1.0),
            dolphin.getRandomY() + 1.6,
            dolphin.getRandomZ(1.0),
            5,
            level.getRandom().nextGaussian() * 0.02,
            level.getRandom().nextGaussian() * 0.02,
            level.getRandom().nextGaussian() * 0.02,
            0.2
        )
    }

    companion object {
        private const val HEART_CHANCE = 0.05f
        private const val NUM_DIGS = 10
        private const val SEARCH_RANGE = 16
    }
}
