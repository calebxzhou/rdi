package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.stats.Stats
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.MangrovePropaguleBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent

object DeadBushConversion {
    private const val SAPLING_DEATH_CHANCE = 0.2f

    @JvmStatic
    fun canSaplingPlaceOn(block: Block, floor: BlockState): Boolean {
        return block !is MangrovePropaguleBlock && floor.`is`(BlockTags.SAND)
    }

    @JvmStatic
    fun isSaplingOnSand(block: Block, level: BlockGetter, pos: BlockPos): Boolean {
        return canSaplingPlaceOn(block, level.getBlockState(pos.below()))
    }

    @JvmStatic
    fun tryKillSaplingOnSand(block: Block, level: ServerLevel, pos: BlockPos, random: RandomSource): Boolean {
        if (!isSaplingOnSand(block, level, pos)) {
            return false
        }

        if (random.nextFloat() < SAPLING_DEATH_CHANCE) {
            level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL)
        }
        return true
    }

    @JvmStatic
    fun tryHydrateDeadBush(context: UseOnContext): Boolean {
        val itemStack = context.itemInHand
        val potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
        if (!potionContents.`is`(Potions.WATER) || context.clickedFace == Direction.DOWN) {
            return false
        }

        val level = context.level
        val blockPos = context.clickedPos
        if (!level.getBlockState(blockPos).`is`(Blocks.DEAD_BUSH)) {
            return false
        }

        val player = context.player ?: return false
        hydrate(level, blockPos, player, context.hand, itemStack)
        return true
    }

    private fun hydrate(level: Level, blockPos: BlockPos, player: Player, hand: net.minecraft.world.InteractionHand, itemStack: ItemStack) {
        level.playSound(null, blockPos, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 1.0f)
        player.setItemInHand(hand, ItemUtils.createFilledResult(itemStack, player, ItemStack(Items.GLASS_BOTTLE)))
        player.awardStat(Stats.ITEM_USED.get(itemStack.item))

        if (level is ServerLevel) {
            for (i in 0..4) {
                level.sendParticles<SimpleParticleType>(
                    ParticleTypes.SPLASH,
                    blockPos.x + level.random.nextDouble(),
                    (blockPos.y + 1).toDouble(),
                    blockPos.z + level.random.nextDouble(),
                    1,
                    0.0,
                    0.0,
                    0.0,
                    1.0
                )
            }
        }

        level.playSound(null, blockPos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f)
        level.gameEvent(null, GameEvent.FLUID_PLACE, blockPos)
        level.setBlockAndUpdate(blockPos, Blocks.BUSH.defaultBlockState())
    }
}
