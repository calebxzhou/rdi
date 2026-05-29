package calebxzhou.rdi.mc.visky.helper

import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Predicate

object AnvilCoalToDiamond {
    private const val COAL_BLOCKS_PER_DIAMOND = 64

    @JvmStatic
    fun tryConvert(fallingBlock: Entity, blockState: BlockState) {
        if (fallingBlock.level().isClientSide() || !blockState.`is`(BlockTags.ANVIL)) {
            return
        }

        val level = fallingBlock.level()
        val coalBlockItems = level.getEntities(
            fallingBlock,
            fallingBlock.boundingBox,
            Predicate { entity: Entity ->
                entity is ItemEntity && entity.item.`is`(Items.COAL_BLOCK)
            }
        )
        coalBlockItems.forEach { compactItem(it as ItemEntity) }
    }

    private fun compactItem(itemEntity: ItemEntity) {
        val itemStack = itemEntity.item
        val diamondCount = itemStack.count / COAL_BLOCKS_PER_DIAMOND
        if (diamondCount <= 0) {
            return
        }

        val diamondEntity = ItemEntity(
            itemEntity.level(),
            itemEntity.x,
            itemEntity.y,
            itemEntity.z,
            ItemStack(Items.DIAMOND, diamondCount)
        )
        diamondEntity.setDefaultPickUpDelay()
        itemEntity.level().addFreshEntity(diamondEntity)

        val remainingCoalBlocks = itemStack.count % COAL_BLOCKS_PER_DIAMOND
        if (remainingCoalBlocks == 0) {
            itemEntity.discard()
        } else {
            itemStack.count = remainingCoalBlocks
        }
    }
}
