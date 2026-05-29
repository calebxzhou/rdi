package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.gameevent.GameEvent

object GoatRamNetherWart {
    @JvmStatic
    fun tryBreak(level: ServerLevel, goat: Goat): Boolean {
        val movement = goat.getDeltaMovement().multiply(1.0, 0.0, 1.0)
        if (movement.lengthSqr() == 0.0) {
            return false
        }

        val hitPos = BlockPos.containing(goat.position().add(movement.normalize()))
        if (!level.getBlockState(hitPos).`is`(Blocks.NETHER_WART_BLOCK)) {
            return false
        }

        if (!level.removeBlock(hitPos, false)) {
            return false
        }

        Block.popResource(level, hitPos, ItemStack(Items.NETHER_WART, level.getRandom().nextInt(2) + 1))
        level.gameEvent(goat, GameEvent.BLOCK_DESTROY, hitPos)
        level.playSound(null, hitPos, SoundEvents.WART_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f)
        return true
    }
}
