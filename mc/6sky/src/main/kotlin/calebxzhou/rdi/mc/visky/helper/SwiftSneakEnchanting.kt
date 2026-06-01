package calebxzhou.rdi.mc.visky.helper

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentInstance
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB

object SwiftSneakEnchanting {
    private const val MAX_WARDEN_DISTANCE = 8.0

    @JvmStatic
    fun tryAddSwiftSneak(level: Level, pos: BlockPos, stack: ItemStack, cost: Int, list: MutableList<EnchantmentInstance>) {
        val box = AABB(pos).inflate(MAX_WARDEN_DISTANCE)
        if (level.getEntitiesOfClass(Warden::class.java, box).isEmpty()) {
            return
        }

        val ench: Holder<Enchantment> = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.SWIFT_SNEAK)

        if (!stack.isPrimaryItemFor(ench)) {
            return
        }

        for (lv in 1..ench.value().maxLevel) {
            if (cost >= ench.value().getMinCost(lv) && cost <= ench.value().getMaxCost(lv)) {
                list.add(EnchantmentInstance(ench, lv))
            }
        }
    }
}
