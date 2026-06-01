package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Drowned
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object SnifferEggDrowned {
    private const val SNIFFER_EGG_CHANCE = 0.01f

    @JvmStatic
    fun tryEquipSnifferEgg(drowned: Drowned): Boolean {
        if (!drowned.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty || drowned.getRandom().nextFloat() >= SNIFFER_EGG_CHANCE) {
            return false
        }

        drowned.setItemSlot(EquipmentSlot.OFFHAND, ItemStack(Items.SNIFFER_EGG))
        drowned.setDropChance(EquipmentSlot.OFFHAND, 0.0f)
        return true
    }

    @JvmStatic
    fun shouldKeepTurtleEgg(entity: Any?): Boolean {
        return entity is Drowned && entity.getItemInHand(InteractionHand.OFF_HAND).`is`(Items.SNIFFER_EGG)
    }

    @JvmStatic
    fun tryPlaceSnifferEgg(level: Level, pos: BlockPos, blockToRemove: Block, remover: Any?): Boolean {
        if (blockToRemove != Blocks.TURTLE_EGG || remover !is Drowned) {
            return false
        }

        val offhand = remover.getItemInHand(InteractionHand.OFF_HAND)
        if (!offhand.`is`(Items.SNIFFER_EGG)) {
            return false
        }

        level.setBlockAndUpdate(pos, Blocks.SNIFFER_EGG.defaultBlockState())
        offhand.shrink(1)
        return true
    }
}
