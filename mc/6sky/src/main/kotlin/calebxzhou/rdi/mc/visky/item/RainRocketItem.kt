package calebxzhou.rdi.mc.visky.item

import net.minecraft.stats.Stats
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.FireworkRocketItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class RainRocketItem(properties: Properties) : FireworkRocketItem(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!level.isClientSide) {
            startRain(level, player, stack, true)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }

    override fun useOn(context: UseOnContext): InteractionResult {
        if (!context.level.isClientSide) {
            startRain(context.level, context.player, context.itemInHand, false)
        }
        return super.useOn(context)
    }

    private fun startRain(level: Level, player: Player?, stack: ItemStack, consume: Boolean) {
        val overworld = level.server?.overworld() ?: return
        overworld.setWeatherParameters(0, RAIN_DURATION, true, false)
        if (player != null) {
            if (consume && !player.abilities.instabuild) {
                stack.consume(1, player)
            }
            player.cooldowns.addCooldown(this, COOLDOWN)
            player.awardStat(Stats.ITEM_USED.get(this))
        } else {
            stack.shrink(1)
        }
    }

    companion object {
        private const val RAIN_DURATION = 6000
        private const val COOLDOWN = 20
    }
}
